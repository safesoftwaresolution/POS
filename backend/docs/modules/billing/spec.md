<!-- spec-version: 2.3 | last-updated: 2026-10-29 -->
# Módulo: Facturación Electrónica (Billing)

## Dependencias
- **Requiere**: sales (`SaleFacade`, evento `SaleCompletedEvent`), customers, products, stores, notifications
- **Requerido por**: `settings` (consulta el token de Factus para sincronizar el logo; pendiente de pasar por una Facade, ver `_roadmap.md`)

> `sales` **no** invoca a billing. Publica `SaleCompletedEvent` y `ElectronicInvoiceEventListener`
> (`@Async` + `@TransactionalEventListener(AFTER_COMMIT)`) emite la factura cuando `sendToFactus` es verdadero.
> Una falla de Factus nunca revierte ni retrasa la venta.

> Desde la v2.0 **ya no depende de `settings`**: las credenciales de Factus salen de `store_billing_config`
> (tabla propia de este módulo), no de la configuración del negocio.

## Estructura Interna Desacoplada

El módulo está internamente segregado por responsabilidades (Feature Slicing & Interface Segregation):
1. **Facturas Electrónicas de Venta**: `InvoiceService` / `BillingInvoiceController` / `InvoiceProvider` / `FactusInvoiceClient`.
2. **Rangos de Numeración DIAN**: `NumberingRangeService` / `BillingNumberingRangeController` / `NumberingRangeProvider` / `FactusNumberingRangeClient`.
3. **Habilitación por local**: `StoreBillingConfigService` / `StoreBillingConfigController` (back office) + `BillingActivationController` (local).
4. **Resolución de credenciales**: `FactusCredentialsResolver` / `FactusTokenManager` / `FactusClientHelper`.
5. **Notas Crédito Electrónicas**: `CreditNoteService` / `BillingCreditNoteController` / `CreditNoteProvider` / `FactusCreditNoteClient`.
6. **Notas Débito Electrónicas**: `DebitNoteService` / `BillingDebitNoteController` / `DebitNoteProvider` / `FactusDebitNoteClient`.
7. **Fachada Unificada**: `BillingFacade` / `BillingServiceImpl` (punto de integración único para otros módulos).

---

## Flujo de habilitación (negocio)

El proveedor (Factus) habilita a la **plataforma**, que compra una bolsa de documentos y la reparte entre
sus locales. Por eso las credenciales llegan al SUPER_ADMIN, no al local:

1. El administrador crea su cuenta, verifica el correo y registra su local. Desde ese momento **puede vender
   sin facturación electrónica**.
2. Si quiere facturar, sube sus documentos (RUT, cámara de comercio, etc., gestionados por el módulo `stores`)
   y solicita la activación.
3. El SUPER_ADMIN revisa los documentos y los tramita ante Factus.
4. Factus entrega credenciales (entorno de pruebas y producción). El SUPER_ADMIN las carga **por local**.
5. El administrador del local hace su trámite de resolución ante la DIAN, registra su rango de numeración
   desde el sistema y **elige cuál usa para facturar** (`POST /api/v1/billing/numbering-ranges/{id}/set-default`).
   El primer rango que registra un local sin rango configurado queda elegido automáticamente. El SUPER_ADMIN
   no necesita conocer el rango: solo lo ve (`sandboxNumberingRangeId` / `productionNumberingRangeId`).
6. Con credenciales activas + rango válido, el local emite facturas electrónicas.

---

## Reglas de Negocio

### RN-BILL-001 — Idempotencia de Emisión
- Si una venta ya cuenta con una factura electrónica en estado `VALIDATED`, no se reenvía a Factus ni se
  duplica la solicitud ante la DIAN (guard bajo bloqueo pesimista sobre `electronic_invoices`).

### RN-BILL-002 — Desacoplamiento y No Bloqueo Local
- El fallo de conexión, timeout o indisponibilidad de la API externa de Factus nunca revierte la transacción
  de venta registrada localmente en el POS. La factura queda en `PENDING` o `ERROR` para su posterior reintento.

### RN-BILL-002b — Emisión por Evento de Dominio (2026-09-24)
- `sales` publica `SaleCompletedEvent` al confirmar la venta. `ElectronicInvoiceEventListener` (`billing/internal/listeners/`) lo atiende con `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`: la emisión a la DIAN solo ocurre una vez confirmada la venta en BD y nunca bloquea el hilo del cajero. Si `sendToFactus = false` el evento se ignora.
- El listener restablece el `TenantContext` del local del evento (`storeId`) durante el procesamiento y lo restaura al terminar.
- `ElectronicInvoice` es una entidad con transiciones de estado e invariantes propios (modelo rico); los estados se tipan con `ElectronicInvoiceStatus` (`billing/dto/`).
- Estructura de paquetes: la API pública de `billing` vive en `api/` (`BillingFacade`), `dto/` y `exception/`, no en la raíz del módulo (ver hallazgo en `_roadmap.md`).

### RN-BILL-003 — Ciclo de Estados de la Factura
| Estado | Significado |
|---|---|
| `PENDING` | Registro inicial creado, pendiente de envío o en espera de reintento por fallo de red/auth temporal |
| `VALIDATED` | Factura validada exitosamente por Factus y la DIAN (contiene CUFE, QR y PDF) |
| `REJECTED` | Rechazada por la DIAN por inconsistencias en los datos fiscales (HTTP 400 / 422) |
| `ERROR` | Error imprevisto, local sin facturación habilitada, o sin rango de numeración configurado |

### RN-BILL-004 — Cache y Renovación de Token OAuth2
- El token se cachea en memoria **por (`store_id`, entorno)**, con un candado por clave (un local lento no
  bloquea a los demás) y margen de renovación de 60 segundos antes de expirar.
- El cache se invalida automáticamente cuando cambian las credenciales del local (huella SHA-256 de las
  credenciales) y ante un `401` del proveedor durante la emisión.

### RN-BILL-005 — Credenciales por Local, sin Respaldo Global
- Cada llamada a Factus usa exclusivamente las credenciales del local correspondiente.
- **No existe fallback a credenciales de plataforma ni a variables de entorno.** Si el local no está `ACTIVE`
  o le faltan credenciales, se lanza `BillingNotEnabledException` y no se hace ninguna llamada al proveedor.
- En variables de entorno solo quedan las **URLs públicas** de cada entorno (`FACTUS_URL_SANDBOX`,
  `FACTUS_URL_PRODUCTION`) y la clave de cifrado (`BILLING_ENCRYPTION_KEY`). Ninguna credencial.

### RN-BILL-006 — Ciclo de Habilitación por Local
```
NOT_REQUESTED → DOCUMENTS_SUBMITTED → AWAITING_PROVIDER → ACTIVE
      ↑                  │                    │             │
      └─── REJECTED ←────┴────────────────────┘   SUSPENDED ←┘
```
| Estado | Quién lo produce | Significado |
|---|---|---|
| `NOT_REQUESTED` | (por defecto, sin fila en BD) | El local vende sin facturación electrónica |
| `DOCUMENTS_SUBMITTED` | ADMINISTRATOR del local | Documentos enviados, esperando revisión del back office |
| `AWAITING_PROVIDER` | SUPER_ADMIN | Documentos aprobados y tramitados; se esperan credenciales de Factus |
| `ACTIVE` | SUPER_ADMIN | Credenciales cargadas y verificadas; el local ya puede facturar |
| `REJECTED` | SUPER_ADMIN | Solicitud rechazada (exige motivo). El local puede corregir y reenviar |
| `SUSPENDED` | SUPER_ADMIN | Facturación pausada (exige motivo). Reactivable |

- No se puede saltar pasos: cada transición se valida contra `BillingActivationStatus.canTransitionTo(...)`.
- Pasar a `ACTIVE` exige credenciales **completas** del entorno activo (client_id, client_secret, usuario y
  contraseña). `REJECTED` y `SUSPENDED` exigen `reason`; al salir de esos estados el motivo se limpia.
- Cada cambio hecho por el SUPER_ADMIN notifica al local (`BILLING_STATUS_CHANGED` vía `NotificationFacade`).
  La solicitud inicial, que origina el propio local, no se autonotifica.

### RN-BILL-007 — Cifrado de Secretos en Reposo
- `client_secret` y `password` se cifran con **AES-256-GCM** mediante `SecretCryptoConverter`
  (`AttributeConverter` de JPA) antes de guardarse.
- Formato almacenado: `v1:` + Base64(IV de 12 bytes ‖ texto cifrado ‖ etiqueta GCM). El prefijo permite rotar
  algoritmo o clave más adelante.
- La clave (32 bytes en Base64) viene de `billing.encryption-key` / `BILLING_ENCRYPTION_KEY` y **es el único
  secreto de facturación que vive fuera de la base de datos**. Si se pierde o cambia, los secretos ya guardados
  dejan de poder leerse.
- Ningún DTO de salida expone secretos: `BillingActivationDto` solo informa si cada entorno está completo
  (`sandboxConfigured` / `productionConfigured`).

### RN-BILL-008 — Guardas de Rangos de Numeración
Aplicados en `NumberingRangeServiceImpl` **antes** de llamar al proveedor:
1. **Rango en uso protegido**: no se puede eliminar ni desactivar el rango que el local tiene configurado como
   su rango activo. Primero debe configurar otro.
2. **Un solo rango activo por tipo de documento**: no se puede activar un rango si ya existe otro activo para
   el mismo `document`.
3. **El consecutivo nunca retrocede**: al actualizar `current`, se consulta el valor real en el proveedor y se
   rechaza cualquier valor menor.
4. **Auditoría obligatoria**: creación, eliminación, activación/desactivación, cambio de consecutivo y elección del
   rango activo (`SET_DEFAULT`) quedan registrados en `numbering_range_audit` con usuario autenticado, valores anterior y nuevo.
5. **Rango activo elegido por el local**: `set-default` solo acepta un rango que el proveedor devuelva con las
   credenciales del propio local (si no lo encuentra → 404), activo y con resolución vigente (si no → 409). Se
   guarda en `store_billing_config` para el **entorno activo** del local (el otro entorno no cambia) y la siguiente
   factura ya lo usa, porque `FactusCredentialsResolver` lo lee de ahí en cada emisión. Al crear un rango, si el
   entorno activo aún no tiene rango, el nuevo queda elegido (con su propia entrada `SET_DEFAULT`).

> Los guardas 1, 2 y 4 dependen del local en contexto (`TenantContext`). Sin local identificado se omiten
> (caso de tests que invocan el servicio directamente); el guarda 3 aplica siempre. `set-default` sí exige local:
> sin él responde `BillingNotEnabledException` (409).

### RN-BILL-009 — Propagación de Errores del Proveedor
Las operaciones sobre Factus ya **no devuelven listas vacías ni `false` silenciosos**. Se traduce el fallo real:

| Situación | Excepción | HTTP |
|---|---|---|
| Local sin facturación habilitada / sin rango | `BillingNotEnabledException` | 409 Conflict |
| Factus objetó los datos enviados (4xx, no auth) | `FactusApiException` (extiende `BillingProviderException`) | 422 Unprocessable Content (`HttpStatus.UNPROCESSABLE_CONTENT`) |
| Factus rechazó las credenciales (401/403) o no responde (5xx, timeout) | `FactusApiException` (extiende `BillingProviderException`) | 502 Bad Gateway |
| El proveedor activo no implementa la operación | `UnsupportedOperationException` | 501 Not Implemented |

El mensaje del proveedor se propaga al cliente (truncado a 500 caracteres) para que el usuario sepa qué corregir.

### RN-BILL-010 — Idempotencia y Aislamiento de Notas Crédito
- Si una referencia (`reference_code`) ya cuenta con una nota crédito en estado `VALIDATED`, no se reenvía a Factus ni se duplica ante la DIAN (guard bajo bloqueo pesimista `findByReferenceCodeForUpdate` sobre `electronic_credit_notes`).
- `reference_code` es `UNIQUE` global: si la referencia pertenece a **otro local** se responde 409 sin llamar a Factus ni exponer la nota existente (mismo criterio que RN-BILL-014).
- Toda operación (emisión, consulta, descarga, eliminación) exige el `storeId` de la solicitud (`TenantContext`); sin local asociado responde 409. **No hay respaldo al local 1** (antes se asumía).

### RN-BILL-011 — Recuperación Automática de Conflicto 409
- Cuando Factus rechaza una creación con HTTP 409 ("Se encontró un documento pendiente por enviar a la DIAN"), el sistema elimina el borrador no validado en Factus y reintenta **una sola vez**. Si el borrador no se puede eliminar, se reporta el conflicto. Aplica a:
  - Notas crédito: `DELETE /v2/credit-notes/reference/{reference_code}`.
  - Notas débito: `DELETE /v2/debit-notes/reference/{reference_code}`.
  - Facturas: `DELETE /v1/bills/destroy/reference/{reference_code}` (`FactusInvoiceClient`).

### RN-BILL-012 — Emisión de Nota Crédito a partir de Venta
- Para emitir nota crédito referenciando una venta (`createFromSale`), la venta debe tener factura electrónica `VALIDATED` **en el local actual**. La nota crédito referencia el número de factura legal (`factus_number`).
- `reference_code` determinista: `NC-<número de factura de la venta>-C<concepto>`. Un doble clic o un reintento reutiliza la misma nota en vez de emitir otra ante la DIAN.
- `sale_id` se persiste al crear la nota (antes se asignaba por reflexión y quedaba nulo).

### RN-BILL-016 — Referencia Determinista de Facturas
- El `reference_code` que se envía a Factus es `<invoiceNumber de la venta>-<saleId>`, **estable entre reintentos** (antes llevaba `System.currentTimeMillis()` y cada reintento tras un timeout podía generar un segundo documento ante la DIAN). Lo mismo para el `code_reference` de ítems sin código.
- Facturar sin local asociado a la venta falla (`IllegalStateException`); no hay respaldo al local 1.

### RN-BILL-017 — Proveedor Configurado
- `billing.provider` (`factus` | `mock`) debe corresponder a un adaptador registrado. Si no existe, **la aplicación no arranca** (`BillingProviderStartupValidator`) y el error lista los proveedores disponibles; si aun así una operación lo resolviera en tiempo de ejecución, también falla con ese listado: no hay respaldo silencioso a `mock`, que emitiría facturas falsas por una errata de configuración.
- `MockBillingAdapter` solo se registra con `billing.provider=mock` (`@ConditionalOnProperty`).
- **Puerto de cuenta (`BillingAccountProvider`)**: todo adaptador (`ElectronicInvoicingProvider`) implementa además `isAccountConfigured(storeId, env)`, `verifyConnection(storeId, env)`, `onAccountChanged(storeId)` y `displayName()`. `StoreBillingConfigServiceImpl` y `BillingActivationMapper` obtienen el proveedor activo a través de `ActiveBillingProvider` (activación a `ACTIVE`, cambio de entorno, prueba de conexión, invalidación de caché tras cargar credenciales, flags `sandboxConfigured`/`productionConfigured`) y ya no dependen de clases `Factus*`. El mock da la cuenta siempre por configurada y la prueba de conexión por exitosa.
- `BillingProviderException` es la base común de los errores de un proveedor (`FactusApiException` la extiende). `GlobalExceptionHandler` aplica el mapeo de RN-BILL-009 a la base y responde **501 Not Implemented** a una `UnsupportedOperationException` (operación no implementada por el proveedor activo).
- La sincronización del logo de `settings` solo se intenta si `billing.provider=factus`.

### RN-BILL-018 — Timeouts de Salida
- Todos los clientes HTTP hacia Factus (`FactusClientHelper`, `FactusTokenManager`) y el sincronizado del logo se crean con `OutboundHttp.restClient()`: conexión 5 s, lectura 45 s. Un proveedor lento ya no deja hilos ni filas bloqueadas indefinidamente.

### RN-BILL-019 — Líneas de Servicio en Facturas y Notas Crédito
- Una línea de venta de **servicio** (`SaleItemDto.itemType = SERVICE`, ver `sales/spec.md` RN-SALE-004) se factura con el `unit_measure_code` y el `unspsc_code` que **la venta guardó** al momento de cobrar (copiados del catálogo de `services`). Billing no consulta el catálogo de servicios: así editar o desactivar el servicio no cambia lo ya facturado, y no depende de `ServiceFacade`.
- Sin esos datos se usan los valores por defecto (unidad `94`, estándar `999`). Las líneas de **producto** siguen resolviendo el UNSPSC desde `ProductFacade`.
- La nota crédito (`createFromSale`) usa los mismos códigos de la línea, para que coincida con la factura que corrige.
- Pendiente: los códigos de unidad de medida para servicios por hora u otras medidas deben validarse contra el catálogo de Factus; hoy la tienda los escribe en el servicio sin verificación.

### RN-BILL-020 — Un Vendedor Solo Ve las Facturas de sus Ventas
- Para el rol `SELLER`, `GET /billing/invoices` lista solo las facturas de las ventas que él registró (consulta nativa sobre `sales.user_id`, con orden fijo por `id` descendente; el parámetro `sort` se ignora). Consultar, descargar el PDF o XML y enviar por correo la factura de una venta ajena responde como inexistente. `ADMINISTRATOR` y `SUPERVISOR` ven todas las del local. Ver `sales/spec.md`, RN-SALE-005.

### RN-BILL-013 — Notas Débito solo con Referencia a Factura
- Solo se emiten notas débito con `customization_id=30` (con referencia a una factura electrónica). El tipo `32` (sin referencia) exige `billing_period` en Factus y `electronic_debit_notes.bill_number` es `NOT NULL`, así que se rechaza con 400.
- Conceptos de corrección DIAN para notas débito (`correction_concept_code`): `1` Intereses, `2` Gastos por cobrar, `3` Cambio del valor, `4` Otros.

### RN-BILL-014 — Idempotencia y Aislamiento de Notas Débito
- Guard bajo bloqueo pesimista (`findByReferenceCodeForUpdate` sobre `electronic_debit_notes`): una referencia ya `VALIDATED` devuelve la nota existente sin reenviarla a Factus; una en `ERROR`/`REJECTED` se reintenta sobre el mismo registro (`prepareForResend`).
- `reference_code` es `UNIQUE` global: si la referencia pertenece a **otro local**, o al mismo local pero para **otra factura**, se responde 409 sin llamar a Factus ni exponer la nota existente.
- Toda operación (emisión, consulta, descarga, eliminación) exige el `storeId` de la solicitud (`TenantContext`); sin local asociado se responde 409. No hay respaldo a un local por defecto.
- Conflicto 409 de Factus: igual que RN-BILL-011, contra `DELETE /v2/debit-notes/reference/{reference_code}`.

### RN-BILL-015 — Nota Débito sobre la Factura de una Venta
- `POST /debit-notes/sales/{saleId}` exige que la venta tenga factura electrónica `VALIDATED` **en el local actual** (404 si no existe, 409 si no está validada). El `bill_number` se toma de esa factura (si el cliente lo envía, debe coincidir; si no, 400) y la nota queda vinculada a la venta (`sale_id`).
- A diferencia de la nota crédito, los ítems los define el cliente: la nota débito cobra un valor adicional (intereses, gastos), no replica la venta.

---

## API Endpoints

### 1. Facturas Electrónicas de Ventas (`BillingInvoiceController`)
- `GET /api/v1/billing/sales/{saleId}` — consultar estado y datos de la factura de una venta.
- `POST /api/v1/billing/sales/{saleId}/retry` — reintentar facturación fallida (`ERROR` o `PENDING`).
- `POST /api/v1/billing/sales/{saleId}/send-email` — enviar ZIP con PDF y XML legal al email indicado.
- `GET /api/v1/billing/sales/{saleId}/pdf` — descargar el PDF legal de la factura.
- `GET /api/v1/billing/sales/{saleId}/xml` — descargar el XML legal de la factura.
- `GET /api/v1/billing/invoices` — historial paginado de facturas del local, filtrable por estado.

### 2. Rangos de Numeración DIAN (`BillingNumberingRangeController`)
- `GET /api/v1/billing/numbering-ranges/dian` — resoluciones asociadas al software ante la DIAN.
- `GET /api/v1/billing/numbering-ranges` — rangos registrados en Factus para el local.
- `GET /api/v1/billing/numbering-ranges/{id}` — detalle de un rango (`null` → 404 si no existe).
- `POST /api/v1/billing/numbering-ranges` — registrar una resolución DIAN. Body: `document`, `prefix`,
  `resolution_number`, `current`. Factus completa desde/hasta, fechas y clave técnica desde la DIAN.
- `DELETE /api/v1/billing/numbering-ranges/{id}` — eliminar. Si el rango ya tuvo facturas, Factus lo marca como
  eliminado en lugar de borrarlo.
- `PATCH /api/v1/billing/numbering-ranges/{id}/toggle-status` — alternar activo/inactivo. Devuelve el rango actualizado.
- `PATCH /api/v1/billing/numbering-ranges/{id}/current` — actualizar el consecutivo (solo hacia adelante).
- `POST /api/v1/billing/numbering-ranges/{id}/set-default` — usar este rango para facturar en el entorno activo
  del local. Devuelve el rango. 404 si no existe en la cuenta del local, 409 si está inactivo o vencido.

### 3. Habilitación — Back Office (`StoreBillingConfigController`, SUPER_ADMIN)
- `GET /api/v1/backoffice/billing?status=DOCUMENTS_SUBMITTED` — cola de revisión por etapa.
- `GET /api/v1/backoffice/stores/{storeId}/billing` — estado del local (sin secretos).
- `PUT /api/v1/backoffice/stores/{storeId}/billing/credentials?environment=SANDBOX|PRODUCTION` — cargar
  credenciales. Un `clientSecret`/`password` vacío **conserva** el valor guardado. El rango de numeración no se carga aquí:
  lo elige el administrador del local (`set-default`).
- `POST /api/v1/backoffice/stores/{storeId}/billing/test-connection?environment=…` — pide un token real a
  Factus sin activar nada. Nunca lanza error por rechazo: devuelve `{success, environment, message}`.
- `PATCH /api/v1/backoffice/stores/{storeId}/billing/environment?environment=…` — cambiar entorno activo.
- `POST /api/v1/backoffice/stores/{storeId}/billing/transition` — cambiar etapa. Body: `{status, reason}`.

### 4. Habilitación — Local (`BillingActivationController`)
- `GET /api/v1/billing/activation/status` — estado de facturación del local autenticado (sin credenciales).
- `POST /api/v1/billing/activation/request` — solicitar activación. Exige al menos un documento ya subido.

### 5. Notas Crédito Electrónicas (`BillingCreditNoteController`)
- `POST /api/v1/billing/credit-notes/validate` — crear y validar nota crédito ante Factus API v2 y la DIAN.
- `POST /api/v1/billing/credit-notes/sales/{saleId}?conceptCode=2&observation=` — emitir nota crédito para anular o ajustar una venta facturada (`conceptCode` por defecto `2` = anulación).
- `GET /api/v1/billing/credit-notes?status=` — historial paginado del local, filtrable por estado.
- `GET /api/v1/billing/credit-notes/{id}` — consultar nota crédito por ID local.
- `GET /api/v1/billing/credit-notes/reference/{referenceCode}` — consultar nota crédito por código de referencia POS.
- `GET /api/v1/billing/credit-notes/{id}/pdf` — descargar PDF legal de la nota crédito (Base64 decodificado como `application/pdf`).
- `GET /api/v1/billing/credit-notes/{id}/xml` — descargar XML legal de la nota crédito (Base64 decodificado como `application/xml`).
- `DELETE /api/v1/billing/credit-notes/reference/{referenceCode}` — eliminar nota crédito no validada en Factus (borrador pendiente o rechazado).

### 6. Notas Débito Electrónicas (`BillingDebitNoteController`)
- `POST /api/v1/billing/debit-notes/validate` — crear y validar nota débito ante Factus API v2 (`POST /v2/debit-notes/validate`). Responde 200 con el `status` resultante (`VALIDATED`, `REJECTED`, `ERROR`).
- `POST /api/v1/billing/debit-notes/sales/{saleId}` — emitir nota débito sobre la factura de una venta (RN-BILL-015). Body: `CreateDebitNoteRequest`.
- `GET /api/v1/billing/debit-notes?status=` — historial paginado del local, filtrable por estado.
- `GET /api/v1/billing/debit-notes/{id}` — consultar nota débito por ID local (404 si no existe en el local).
- `GET /api/v1/billing/debit-notes/reference/{referenceCode}` — consultar por código de referencia.
- `GET /api/v1/billing/debit-notes/{id}/pdf` — PDF legal (`application/pdf`). 404 si no existe, 409 si no está validada.
- `GET /api/v1/billing/debit-notes/{id}/xml` — XML legal (`application/xml`). 404 si no existe, 409 si no está validada.
- `DELETE /api/v1/billing/debit-notes/reference/{referenceCode}` — eliminar en Factus una nota débito no validada. 204 si se eliminó, 400 si Factus no pudo, 409 si ya está validada.

---

## Modelo de Datos

### Tabla: `electronic_invoices`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGSERIAL | PK, Auto-increment |
| store_id | BIGINT | NOT NULL, FK → stores(id) (desde `V13`) |
| sale_id | BIGINT | UNIQUE, NOT NULL, FK → sales(id) |
| factus_number | VARCHAR(50) | NULLABLE |
| cufe | VARCHAR(255) | NULLABLE |
| qr_code | TEXT | NULLABLE |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' |
| error_message | TEXT | NULLABLE |
| pdf_url | VARCHAR(500) | NULLABLE |
| validated_at | TIMESTAMP | NULLABLE |
| created_at / updated_at | TIMESTAMP | NOT NULL |

### Tabla: `store_billing_config` (`V14__Store_Billing_Config.sql`)
Una fila por local (relación 1 a 1 con `stores`).

| Campo | Tipo | Restricciones |
|---|---|---|
| store_id | BIGINT | PK, FK → stores(id) |
| status | VARCHAR(30) | NOT NULL, DEFAULT 'NOT_REQUESTED', CHECK (ver RN-BILL-006) |
| status_reason | VARCHAR(500) | NULLABLE (motivo de rechazo/suspensión) |
| environment | VARCHAR(12) | NOT NULL, DEFAULT 'SANDBOX', CHECK IN ('SANDBOX','PRODUCTION') |
| sandbox_client_id | VARCHAR(255) | NULLABLE |
| sandbox_client_secret | VARCHAR(1024) | NULLABLE, **cifrado AES-GCM** |
| sandbox_username | VARCHAR(255) | NULLABLE |
| sandbox_password | VARCHAR(1024) | NULLABLE, **cifrado AES-GCM** |
| sandbox_numbering_range_id | INT | NULLABLE |
| production_client_id | VARCHAR(255) | NULLABLE |
| production_client_secret | VARCHAR(1024) | NULLABLE, **cifrado AES-GCM** |
| production_username | VARCHAR(255) | NULLABLE |
| production_password | VARCHAR(1024) | NULLABLE, **cifrado AES-GCM** |
| production_numbering_range_id | INT | NULLABLE |
| updated_by | VARCHAR(100) | NULLABLE (usuario que hizo el último cambio) |
| created_at / updated_at | TIMESTAMP | NOT NULL |

Índice: `IDX_store_billing_config_status` (cola de revisión del back office).

### Tabla: `numbering_range_audit` (`V15__Numbering_Range_Audit.sql`, `V17__Numbering_Range_Audit_Set_Default.sql`)
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGSERIAL | PK |
| store_id | BIGINT | NOT NULL, FK → stores(id) |
| range_id | BIGINT | NOT NULL (id del rango en el proveedor) |
| action | VARCHAR(20) | NOT NULL, CHECK IN ('CREATE','DELETE','ACTIVATE','DEACTIVATE','UPDATE_CURRENT','SET_DEFAULT') (V17) |
| previous_value / new_value | VARCHAR(50) | NULLABLE |
| performed_by | VARCHAR(100) | NULLABLE |
| created_at | TIMESTAMP | NOT NULL |

Índice: `IDX_numbering_range_audit_store_range`.

> `V16__Drop_Settings_Factus_Columns.sql` eliminó las columnas `factus_*` de `settings` (obsoletas desde V14).
> Antes de borrarlas traslada a `store_billing_config` lo que no es secreto (client_id, username y rango); los
> secretos **no** se pueden trasladar por SQL porque el cifrado ocurre en la capa Java, así que el local queda
> en `NOT_REQUESTED` hasta que el SUPER_ADMIN vuelva a cargarlos.

### Tabla: `electronic_credit_notes` (`V23__Credit_Notes.sql`)
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGSERIAL | PK, Auto-increment |
| store_id | BIGINT | NOT NULL, FK → stores(id) |
| sale_id | BIGINT | NULLABLE, FK → sales(id) (se persiste al emitir desde una venta) |
| reference_code | VARCHAR(100) | NOT NULL, UNIQUE |
| bill_number | VARCHAR(50) | NOT NULL (factura electrónica referenciada) |
| credit_note_number | VARCHAR(50) | NULLABLE |
| correction_concept_code | VARCHAR(10) | NOT NULL |
| customization_id | VARCHAR(10) | NOT NULL, DEFAULT '20' |
| cude | VARCHAR(255) | NULLABLE |
| qr_code | TEXT | NULLABLE |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' |
| error_message | TEXT | NULLABLE |
| pdf_url | VARCHAR(500) | NULLABLE |
| validated_at | TIMESTAMP | NULLABLE |
| created_at / updated_at | TIMESTAMP | NOT NULL |

Índices: `IDX_electronic_credit_notes_store`, `IDX_electronic_credit_notes_store_status`, `IDX_electronic_credit_notes_bill`.

### Tabla: `electronic_debit_notes` (`V24__Debit_Notes.sql`)
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGSERIAL | PK, Auto-increment |
| store_id | BIGINT | NOT NULL, FK → stores(id) |
| sale_id | BIGINT | NULLABLE, FK → sales(id) (solo si se emitió desde una venta) |
| reference_code | VARCHAR(100) | NOT NULL, UNIQUE |
| correction_concept_code | VARCHAR(10) | NOT NULL |
| customization_id | VARCHAR(10) | NOT NULL, DEFAULT '30' |
| bill_number | VARCHAR(50) | NOT NULL (factura electrónica referenciada) |
| debit_note_number | VARCHAR(50) | NULLABLE (número legal asignado por Factus/DIAN) |
| cude | VARCHAR(255) | NULLABLE |
| qr_code | TEXT | NULLABLE |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' |
| error_message | TEXT | NULLABLE |
| pdf_url | VARCHAR(500) | NULLABLE |
| validated_at | TIMESTAMP | NULLABLE |
| created_at / updated_at | TIMESTAMP | NOT NULL |

Índices: `IDX_electronic_debit_notes_store`, `IDX_electronic_debit_notes_store_status`, `IDX_electronic_debit_notes_bill`.

---

## Permisos

### Facturas, notas crédito/débito y rangos de numeración
| Operación | SUPER_ADMIN | Admin Local | Supervisor | Vendedor |
|---|---|---|---|---|
| Consultar factura de venta | - | ✓ | ✓ | ✓ |
| Descargar PDF / XML legal | - | ✓ | ✓ | ✓ |
| Reintentar emisión | - | ✓ | ✓ | - |
| Enviar factura por email | - | ✓ | ✓ | ✓ |
| Emitir / validar nota crédito | - | ✓ | ✓ | - |
| Consultar notas crédito | - | ✓ | ✓ | ✓ |
| Descargar PDF / XML nota crédito | - | ✓ | ✓ | ✓ |
| Eliminar nota crédito no validada | - | ✓ | ✓ | - |
| Emitir / validar nota débito | - | ✓ | ✓ | - |
| Consultar notas débito | - | ✓ | ✓ | ✓ |
| Descargar PDF / XML nota débito | - | ✓ | ✓ | ✓ |
| Eliminar nota débito no validada | - | ✓ | ✓ | - |
| Consultar rangos DIAN / Factus | - | ✓ | ✓ | - |
| Crear / eliminar / activar rango | - | ✓ | - | - |
| Actualizar consecutivo | - | ✓ | - | - |
| Elegir rango para facturar | - | ✓ | - | - |

### Habilitación de facturación
| Operación | SUPER_ADMIN | Admin Local | Supervisor |
|---|---|---|---|
| Ver estado de facturación del propio local | - | ✓ | ✓ |
| Solicitar activación | - | ✓ | - |
| Ver cola de revisión / estado de cualquier local | ✓ | - | - |
| Cargar credenciales / probar conexión | ✓ | - | - |
| Cambiar etapa o entorno | ✓ | - | - |

> El administrador del local **nunca** ve ni envía credenciales: ningún endpoint bajo `/api/v1/billing/activation`
> las acepta ni las devuelve.

---

## Pendiente / no verificado

- **Llamada a Factus dentro de la transacción con bloqueo**: `InvoiceServiceImpl.processElectronicInvoice` sigue
  llamando al proveedor mientras sostiene el `SELECT … FOR UPDATE` de la factura. Con los timeouts de RN-BILL-018 la espera
  está acotada, pero lo correcto es dividirla en reservar (`PENDING`) → llamar → registrar resultado.
- **Reintento automático**: no hay job que reintente facturas `PENDING`/`ERROR` ni outbox; si el proceso cae tras el commit de
  la venta y antes de emitir, la factura queda sin emitir hasta un `retry` manual.
- **Estado `PENDING` por fallo temporal**: los clientes Factus devuelven `InvoiceResult.pending(...)` ante fallos de red o
  autenticación, pero `InvoiceServiceImpl.applyResultToEntity` solo distingue `VALIDATED`/`REJECTED` y trata el resto como
  `ERROR`. Decidir si se añade `markAsPending` o se ajusta RN-BILL-002.
- **Códigos DIAN hardcodeados**: unidad `94`, tributo `01`, municipio `11001` por defecto y el mapeo de medios de pago
  (`TRANSFER→31`, `NEQUI→42`, `CARD→48`) están repetidos en `FactusSaleMapper`, `BillingCanonicalMapper` y
  `CreditNoteServiceImpl`; hay que centralizarlos (el catálogo `PAYMENT_METHODS.extra_value` ya existe).
- **Cifrado de secretos**: la clave se valida de forma perezosa (no al arrancar) y `v1:` no lleva `key id`, así que no hay
  rotación real de `BILLING_ENCRYPTION_KEY`.

- **Notas débito contra el sandbox real de Factus**: rutas y payload siguen la documentación de Factus
  (`/v2/debit-notes/validate`, `/v2/debit-notes/{number}/download-pdf|download-xml`,
  `/v2/debit-notes/reference/{reference_code}`), pero esa documentación **no muestra un JSON de respuesta** de
  creación; solo menciona `data.number`. `FactusDebitNoteClient` lee el número de `data.debit_note`, `data.bill`
  o `data` (en ese orden) y el QR/URL pública de `data.links`, sin haberlo confirmado contra una respuesta real.
- **Contra el sandbox real de Factus no se ha verificado**:
  - El sobre (envelope) exacto de la respuesta de `GET /v2/numbering-ranges/dian`. La documentación de Factus
    para este endpoint ("Rangos Asociados al Software") solo describe los campos de cada rango —`prefix`,
    `from`, `to`, `resolution_number`, `start_date`, `end_date`, `technical_key`— sin mostrar un JSON de
    ejemplo, así que se desconoce si la respuesta es una lista en `data`, una lista anidada en `data.data`, o
    un rango único en `data`. `FactusNumberingRangeMapper.extractRangesList` tolera las tres formas de forma
    defensiva, sin haberlo confirmado contra una respuesta real.
  - Que este endpoint **no** documenta `id`, `current`, `is_active`, `document_name` ni `is_expired` —campos
    que `NumberingRangeDto`/`mapToDto` sí intentan extraer de cualquier respuesta de rango—. Tiene sentido
    porque `/dian` lista resoluciones autorizadas por la DIAN que el local aún no ha registrado en Factus (sin
    `id` propio todavía), a diferencia de `GET /v2/numbering-ranges` (rangos ya registrados, con `id`,
    `current` e `is_active`). Pero no está verificado que sea así de tajante: falta confirmar que esos campos
    realmente vienen `null`/ausentes en `/dian` y no bajo otro nombre, y que el consumidor de
    `queryDianNumberingRanges()` (pensado para poblar el flujo de "registrar rango") tolera bien un DTO con
    `id`/`current` nulos.
  - El tipo del campo `current` al crear un rango: `CreateNumberingRangeRequest.current` es `Long` en la API
    propia, pero `FactusNumberingRangeMapper.toCreatePayload` lo serializa como texto
    (`String.valueOf(request.current())`) porque así lo describe la documentación de Factus para "crear rango".
    No se ha probado si Factus también acepta (o exige) un número JSON allí, ni si `PATCH .../current` —que
    sí envía `current` como número (`Map.of("current", current)`)— es consistente con eso.
  - Los límites de `resolution_number`: `CreateNumberingRangeRequest.resolutionNumber` solo valida `@NotBlank`,
    sin `@Size` ni patrón. No se conoce la longitud máxima ni el formato que la DIAN/Factus exige realmente;
    un valor fuera de rango hoy solo se descubre cuando Factus responde error y `FactusClientHelper.translate`
    lo traduce a `FactusApiException`.
- **Validación de vigencia y agotamiento en cada emisión** (`is_expired`, `current <= to`): diseñado pero no
  implementado. Confirmado que `BillingServiceImpl`/`InvoiceService` no referencian `NumberingRangeService` ni
  `NumberingRangeDto` en ningún punto del flujo de emisión — una venta se envía a Factus sin ninguna
  comprobación previa de que el rango asociado siga vigente o tenga cupo. Si el rango está agotado o vencido,
  el único freno es el rechazo que devuelva Factus, que llega como una factura en estado `ERROR` después de
  gastar la llamada (y, según el caso, después de que la venta ya se registró en el POS). Falta decidir:
  - Si la comprobación se hace con una llamada a Factus por venta (`GET /v2/numbering-ranges/{id}`, coste de
    latencia y de cuota de API) o con un cacheo local del rango (riesgo de desincronizarse si el rango se
    agota o se desactiva desde el panel de Factus fuera del POS).
  - Dónde vive la alerta de "rango próximo a agotarse" (umbral, a quién notifica, si usa el módulo
    `notifications`) — no hay ni diseño de umbral todavía, solo la constatación de que falta.
- **Frontend**: no hay pantallas para ninguno de estos flujos todavía.
