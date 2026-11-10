<!-- spec-version: 4.1 | last-updated: 2026-11-05 -->
# Módulo: Facturación Electrónica (Billing)

## Dependencias
- **Requiere**: sales (`SaleFacade`, evento `SaleCompletedEvent`), customers, products, stores, notifications
- **Requerido por**: otros módulos solo a través de `BillingFacade`

> `sales` **no** invoca a billing. Publica `SaleCompletedEvent` y `ElectronicInvoiceEventListener`
> (`@Async` + `@TransactionalEventListener(AFTER_COMMIT)`) emite la factura cuando `sendElectronicInvoice` es verdadero (el nombre anterior, `sendToFactus`, se sigue aceptando al recibir la solicitud).
> Una falla del proveedor nunca revierte ni retrasa la venta.

> **No depende de `settings`**: el estado de facturación de cada local vive en `store_billing_config` (tabla propia de este módulo).
> Factus fue retirado del código: ya no es un proveedor soportado.

## Estructura Interna Desacoplada

El módulo está internamente segregado por responsabilidades (Feature Slicing & Interface Segregation):
1. **Facturas Electrónicas de Venta**: `InvoiceService` / `BillingInvoiceController` / `InvoiceProvider` / `AlegraInvoiceClient`.
2. **Rangos de Numeración DIAN**: `NumberingRangeService` / `BillingNumberingRangeController` / `NumberingRangeProvider` / `AlegraNumberingRangeClient` / `AlegraNumberAllocator`.
3. **Habilitación por local**: `StoreBillingConfigService` / `StoreBillingConfigController` (back office) + `BillingActivationController` (local).
4. **Resolución de credenciales**: `AlegraAccessResolver` / `AlegraCompanyProvider` / `AlegraClientHelper`.
5. **Notas Crédito Electrónicas**: `CreditNoteService` / `BillingCreditNoteController` / `CreditNoteProvider` / `AlegraNoteClient`.
6. **Notas Débito Electrónicas**: `DebitNoteService` / `BillingDebitNoteController` / `DebitNoteProvider` / `AlegraNoteClient`.
7. **Fachada Unificada**: `BillingFacade` / `BillingServiceImpl` (punto de integración único para otros módulos).

---

## Proveedores de facturación

`billing.provider` (variable `BILLING_PROVIDER`) elige el adaptador. Un valor desconocido **aborta el arranque** (RN-BILL-017).

| Valor | Adaptador | Estado |
|---|---|---|
| `alegra` (**por defecto**) | `AlegraBillingAdapter` (`billing/internal/adapters/alegra/`) | Facturas, notas crédito y débito, PDF, XML y correo. Verificado contra el sandbox de Alegra |
| `mock` | `MockBillingAdapter` | Solo desarrollo; se registra únicamente con `billing.provider=mock` |

Los servicios dependen de los puertos (`InvoiceProvider`, `CreditNoteProvider`, `DebitNoteProvider`, `NumberingRangeProvider`, `BillingAccountProvider`), nunca de la clase de un adaptador concreto. Los beans de Alegra existen solo con `billing.provider=alegra`.

> **Factus fue retirado por completo**: el código antes y, con `V41`, sus credenciales cifradas (`store_billing_config`), el endpoint `PUT …/billing/credentials`, la clave `BILLING_ENCRYPTION_KEY` y los nombres `factus_number` / `factusNumber` / `sendToFactus` (hoy `legal_number` / `legalNumber` / `sendElectronicInvoice`). `store_billing_config` conserva el rango de numeración activo de cada entorno.

---

## Proveedor Alegra

### Modelo de cuentas
- **Un token de plataforma por entorno** (`ALEGRA_API_TOKEN_SANDBOX` / `ALEGRA_API_TOKEN_PRODUCTION`), enviado como `Authorization: Bearer`. Vive solo en el entorno de despliegue: nunca en la base, en el repositorio ni en los logs.
- **Cada local es una empresa asociada** en Alegra con su propio `company.id` (ULID), guardado por local y entorno en `store_billing_config.sandbox_company_id` / `production_company_id`. El de producción lo guarda el asistente de autoservicio al crear la empresa (ver «Asistente de habilitación»); el SUPER_ADMIN también puede cargarlo con `PUT /api/v1/backoffice/stores/{storeId}/billing/company-id?environment=` (body `{ "companyId": "…" }`). Las respuestas solo dicen si está cargado (`sandboxCompanyIdConfigured` / `productionCompanyIdConfigured`), nunca su valor.
- **El aislamiento entre locales es por `company.id` + `TenantContext`**, no por token: `AlegraAccessResolver` resuelve el acceso del local en contexto y nunca recurre al `company.id` de otro. Con el token de plataforma, un `company.id` equivocado sería facturar como otra empresa; por eso nadie lo escribe a mano salvo el SUPER_ADMIN, y el asistente solo guarda el que Alegra devolvió al crear la empresa del local en contexto.
- `isAccountConfigured` = hay token del entorno **y** el local tiene `company.id`. Con `billing.provider=alegra` y sin tokens la aplicación arranca y la cuenta figura como no configurada.
- Los mensajes de `AlegraAccessResolver` dicen por separado si falta el token del servidor o la empresa (`company.id`) del local, con el entorno y el estado en español («entorno de producción», «no solicitada»), nunca el nombre del enum.

### Alta de una empresa en Alegra (guía operativa)
1. **Crear la empresa** (`POST /companies`): nombre, NIT (`identification`), DV y `useAlegraCertificate`. Queda como empresa `associated`.
2. **Completar sus datos** (`PATCH /companies/{id}`): `organizationType`, `identificationType`, `regimeCode`, `email`, `phone` y `address{address, city, department, country}`. Alegra rechaza las facturas (400, `AEP6012`, `AEP6013`, `AEP6015`, `AEP6016`) si faltan. El mapeador de facturas completa por defecto el tipo de identificación (`31`, NIT), el tipo de organización (`1`, jurídica) y el régimen (`R-99-PN`) cuando la empresa no los trae, pero lo correcto es dejarlos guardados.
3. **Activar las notificaciones por correo** de la empresa (`notificationByEmail.enabled = true`); en una empresa nueva vienen apagadas y sin eso el correo de la factura no sale.
4. **Habilitarla ante la DIAN** (`POST /test-sets`, `type: invoices`): en el sandbox con el `governmentId` fijo de la documentación de Alegra; en producción con el código que genera la DIAN al asociar a Alegra como proveedor tecnológico. La empresa pasa a `governmentStatus.invoices = AUTHORIZED`.
5. **Cargar el `company.id`** en el sistema (`PUT …/billing/company-id`) y probar con `POST …/billing/test-connection`.
6. **Registrar el rango de numeración** de facturas (`POST /api/v1/billing/numbering-ranges`, documento `01`) con la resolución real, el rango `from`/`to`, las fechas de vigencia y la clave técnica, y elegirlo con `set-default`. Para notas crédito y débito se registra un rango con documento `91` / `92` y prefijo propio: **no llevan resolución** (en `resolution_number` basta cualquier texto).
7. Pasar el local a `ACTIVE` (RN-BILL-006).

> Producción: cómo obtiene la plataforma su token de producción de Alegra y si cambia por entorno **no está documentado** y debe confirmarse con Alegra antes del despliegue.

### Alta en autoservicio (`BillingOnboardingProvider`)
Puerto en `billing/internal/adapters/` que da de alta la empresa del local ante el proveedor con el acceso de plataforma del entorno (sin `company.id` previo, `AlegraAccessResolver.platformAccess`):
- `createCompany` (`POST /companies`, con `useAlegraCertificate=true`: el dueño no sube certificado ni documentos) y `updateCompany` (`PATCH /companies/{id}`).
- `startDianEnablement` (`POST /test-sets`, `type=invoices`, con el TestSetId que la DIAN le dio al dueño) y `readEnablement` (`governmentStatus.invoices` de `GET /companies/{id}`; `AUTHORIZED` = habilitada).
- `listResolutions` (`GET /resolutions/{nit}`, solo producción; `AEP9009` = la DIAN aún no habilita a la empresa, `AEP9008` = el NIT no es de una empresa de la plataforma).
- `isPlatformReady`: existe el token de plataforma del entorno.

La implementación la elige `billing.provider`: `AlegraOnboardingClient` o `MockOnboardingProvider` (empresa `mock-company-<nit>` ya habilitada y una resolución SETP de prueba). Los locales se habilitan directo en **producción**: el sandbox de Alegra solo factura con el NIT de prueba 900559088 (resolución SETP 18760000001), así que queda para las pruebas de la plataforma.

### Asistente de habilitación (`BillingSetupService`, `/api/v1/billing/setup`)
El administrador del local habilita su facturación solo, en producción, en cuatro pasos. `GET /api/v1/billing/setup` devuelve `status`, `environment`, `platformReady` (hay token de producción), `steps`, `profile` e `invoiceRangesCount` (rangos de facturas en producción). Cada paso es `{ key, state, message }` con `state` `DONE`, `CURRENT` (el paso en que va), `PENDING` (falta uno anterior) o `ERROR` (el mensaje dice qué falló, sin trazas ni «Error en proveedor»):

| Paso | Hecho cuando |
|---|---|
| `FISCAL_DATA` | Hay perfil fiscal guardado **y** el local tiene `company.id` de producción |
| `DIAN_ENABLEMENT` | `readEnablement` responde `AUTHORIZED` (un local `ACTIVE`/`SUSPENDED` con datos fiscales cuenta como hecho) |
| `NUMBERING` | Hay un rango de facturas (`01`) utilizable en producción: activo, vigente hoy y con cupo |
| `READY` | El local está `ACTIVE` (`SUSPENDED` sale en `ERROR` con el motivo) |

- **La DIAN solo se consulta** (`readEnablement`) si el local está `IN_SETUP` y ya tiene su empresa: a un local `ACTIVE` no se le consulta Alegra en cada carga.
- `profile`: los datos guardados (`saved: true`) o una sugerencia con el nombre, NIT (separando o calculando el DV), correo, teléfono y dirección del local (`StoreFacade`).
- **`PUT …/fiscal-profile`** (ADMINISTRATOR, `FiscalProfileRequest` con Bean Validation: razón social 5–450, NIT solo dígitos 3–15, DV de un dígito igual al módulo 11 de la DIAN (`DianCheckDigit`, mismo cálculo que el frontend), tipo de organización 1 o 2, responsabilidades `R-99-PN`/`O-13`/`O-15`/`O-23`/`O-47` separadas por `;`, CIIU opcional de 4 dígitos, correo, teléfono opcional, dirección, municipio DANE de 5 dígitos y departamento igual a sus dos primeros dígitos). En tres pasos: (1) transacción corta que guarda el perfil, crea la fila del local si no existe, pasa `NOT_REQUESTED` → `IN_SETUP` y deja el entorno en `PRODUCTION` mientras no esté `ACTIVE` (`startSelfServiceSetup`); (2) sin transacción, `createCompany` o `updateCompany` si ya tiene empresa; (3) transacción corta que guarda el `company.id` (`updateProviderCompanyId`, que también invalida la caché). Sin token de producción responde 200 con `FISCAL_DATA` en `ERROR`: «La facturación electrónica aún no está disponible en la plataforma. Guardamos tus datos y te avisaremos.» Un rechazo del proveedor también vuelve como `ERROR` del paso (los datos quedan guardados).
- **`POST …/dian-enablement`** (ADMINISTRATOR, `{ "testSetId": "<uuid>" }`; formato inválido → 400 «El código del set de pruebas no tiene el formato correcto»). Exige `FISCAL_DATA` hecho (si no, 409). Llama a `startDianEnablement(PRODUCTION, companyId, testSetId)` y devuelve el asistente con lo que responde la DIAN.
- **`POST …/numbering/import`** (ADMINISTRATOR). Exige la habilitación de la DIAN, el entorno `PRODUCTION` y que el local sea el de la solicitud (409 si no). Pide `listResolutions(PRODUCTION, nit)` y registra cada resolución vigente que aún no esté registrada como rango `01` por el camino normal (`NumberingRangeService.createNumberingRange`: mismas validaciones, guardas RN-BILL-008 y regla de «el primer rango queda elegido»). Las vigentes hoy y que vencen más tarde van primero; las vencidas y las que rechazan las guardas (por ejemplo, ya hay un rango de facturas activo) se informan en `skipped`. Con un rango de facturas activo, crea los rangos de notas que falten: `91` prefijo `NC` y `92` prefijo `ND`, del 1 al 99.999.999, resolución `SIN-RESOLUCION`, vigentes desde hoy por 10 años (las notas no llevan resolución). Responde `{ importedCount, noteRangesCreated, skipped, setup }`.
- **Activación automática**: tras cada una de las tres escrituras y en cada `GET`, si el local está `IN_SETUP`, la DIAN lo habilitó y tiene numeración de facturas utilizable, pasa a `ACTIVE` con actor `system` por la transición normal (que vuelve a aplicar la guarda de RN-BILL-006 y notifica al local). Si la guarda lo rechaza, `READY` sale en `ERROR` con el motivo.
- **Back office**: `GET /api/v1/backoffice/stores/{storeId}/billing/setup` (SUPER_ADMIN, `BackofficeBillingSetupController`) devuelve el mismo `BillingSetupDto` para el local de la ruta (nunca el del contexto), para seguir el avance sin entrar al local. Como el `GET` del local, si ya cumple todo lo activa (actor `system`).

### Catálogo DIVIPOLA (`/api/v1/billing/catalogs/divipola`)
`backend/src/main/resources/billing/divipola.json` (DANE DIVIPOLA, datos.gov.co `gdxc-w37w`, corte 30-dic-2024, CC BY-SA 4.0): 33 departamentos y 1.122 municipios `{ source, departments:[{code,name}], municipalities:[{code,name,departmentCode}] }`. `DivipolaCatalog` lo carga una vez al arrancar en un `DivipolaCatalogDto` inmutable (si falta o está vacío, la aplicación no arranca). `GET /api/v1/billing/catalogs/divipola` (`BillingCatalogController`; SUPER_ADMIN y cualquier rol de local) lo sirve con `Cache-Control: max-age=86400`. Lo usan el paso «Datos de tu RUT» y el registro de clientes fiscales para elegir el municipio por nombre en lugar de escribir su código.

### Frontend del asistente
- `InvoicingBento` → pestaña «Habilitación y rangos» (`ActivationTab`): `BillingSetupWizard` guiado por `GET /billing/setup` (`useLoad`), con `Stepper` (`components/ui`) y un panel por paso: **Datos de tu RUT** (NIT con DV calculado, tipo de persona, responsabilidades con su nombre del RUT, departamento + municipio DIVIPOLA, CIIU opcional; los errores de validación se muestran junto a cada campo; sin token de producción, aviso «Guardamos tus datos. Te avisaremos cuando la facturación esté disponible.»), **Habilítate en la DIAN** (guía del portal de habilitación y envío del TestSetId), **Numeración** (guía MUISCA, «Traer mis rangos de la DIAN» con lo importado y lo omitido, y registro manual plegado donde las notas `91`/`92` no piden resolución ni clave técnica) y **Listo**. El SUPERVISOR ve el avance sin formularios. Con el local `ACTIVE` se muestran debajo los rangos; la estrella «para facturar» solo en rangos de facturas (`01`).
- Facturas emitidas: insignia «Prueba» si `environment = SANDBOX`; «Reintentar» también en `REJECTED`, con una confirmación que pide corregir antes los datos (por ejemplo, del cliente).
- Back office (`BillingBento`): el detalle del local muestra el avance (Stepper de solo lectura) y el resumen de sus datos fiscales; el cambio de entorno pide confirmación (en pruebas las facturas no tienen validez); la carga manual del `company.id` queda en «Avanzado».

### Numeración local
Alegra no asigna el consecutivo: el sistema envía el `number` y la `resolution` completa en cada factura.
- Tablas `billing_numbering_range` (rangos por local, entorno y documento `01`/`91`/`92`) y `billing_document_number` (libro de números asignados, único por referencia y por `(range_id, number)`).
- `AlegraNumberAllocator.allocate(store, env, documento, referencia)` corre en su propia transacción y toma el lock pesimista de la fila del rango activo: emisiones simultáneas reciben números únicos y consecutivos, sin huecos. Es **idempotente por referencia**: reintentar devuelve el mismo número y la misma llave de idempotencia.
- Entre los rangos activos del documento usa primero el **rango elegido por el local** para ese entorno (`store_billing_config.sandbox_numbering_range_id` / `production_numbering_range_id`) si está vigente y con cupo; si no, el primero utilizable como antes. El lock se toma sobre todos los rangos activos del documento, así que el elegido también queda bloqueado.
- Falla con mensaje accionable si no hay rango activo, está vencido, aún no es vigente o está agotado.
- `AlegraNumberingRangeClient` implementa la administración de rangos sobre esas tablas con la misma semántica que el puerto (el servicio de rangos y sus guardas RN-BILL-008 no cambian). Un cambio de consecutivo nunca baja de un número ya asignado.
- Llamar a `allocate` **fuera de cualquier transacción abierta**: usa una conexión propia y desde dentro de otra puede agotar el pool o bloquearse a sí mismo.

### Emisión de facturas (tres pasos)
`InvoiceServiceImpl.processElectronicInvoice` ya no es una transacción larga:
1. **Reservar** (`InvoiceEmissionTransactions.reserve`, transacción corta con lock): crea o bloquea la fila, y si no está validada ni tiene una reserva vigente (`processing_started_at` + `billing.emission.lease`, 5 min) la deja `PENDING`, cuenta el intento y toma la reserva.
2. **Llamar al proveedor sin transacción ni lock**. Dos emisiones simultáneas de la misma venta llaman al proveedor una sola vez; la segunda recibe la factura en curso.
3. **Registrar el resultado** (`complete`, transacción corta): libera la reserva y programa `next_retry_at`.

Reintento automático (`InvoiceRetryJob`, `billing.retry.enabled`, apagado en el perfil de test): cada `billing.retry.interval` ms (60 000) toma hasta `billing.retry.batch-size` (20) facturas `ERROR`/`PENDING` con el reintento vencido o la reserva caducada y menos de `billing.retry.max-attempts` (8) intentos. Espera `min(2^intentos minutos, 60)` más hasta 10 % de variación. Nunca reintenta `REJECTED` ni `VALIDATED`; los locales sin facturación `ACTIVE` se excluyen en la consulta misma (si se omitieran después, sus facturas llenarían el lote y las de los demás locales no se reintentarían nunca); un fallo no detiene el lote. Una factura que no se pudo emitir porque el local no puede facturar (`BillingNotEnabledException`: facturación no activa, sin empresa o sin rango usable) queda en `ERROR` **sin reintento programado**: reintentarla sola no lo arregla y emitirla cuando el local se active saldría con fecha atrasada; se puede reintentar a mano. El reintento manual (`retry`) reinicia el conteo sin pisar una reserva vigente.

### Decisión del resultado (nunca por el código HTTP)
Un `201` solo dice que Alegra recibió el documento; un rechazo de la DIAN también llega como `201`.

| Respuesta de Alegra | Resultado | Notas |
|---|---|---|
| `legalStatus` `ACCEPTED` o `ACCEPTED_WITH_OBSERVATIONS` | `VALIDATED` | Se guardan número legal, CUFE/CUDE, QR y `providerDocumentId`; la llave queda cerrada |
| `legalStatus` `REJECTED` | `REJECTED` | Motivos de la DIAN; la llave se cierra y el siguiente intento usa otra conservando el número |
| `status` `FAILED` | `ERROR` | Igual: llave cerrada |
| `WAITING_RESPONSE`, `REGISTERED` o `SENT` sin `legalStatus` | `PENDING` | Se guarda el `providerDocumentId`; se concilia, no se vuelve a emitir |
| HTTP 400 | `REJECTED` | Datos a corregir; llave cerrada |
| HTTP 422 (llave reutilizada con otro cuerpo) | `PENDING` | Se rota la llave |
| HTTP 401/403, 5xx, timeout o 409 en curso | `PENDING` | Reintento con la **misma** llave |
| Otro 4xx (404…) | `ERROR` | |

Los mensajes de error propios evitan el texto «409» (convención heredada de Factus; ya no hay lógica que dependa de él).

### Conciliación e idempotencia
- Cada documento viaja con la `Idempotency-Key` (UUID v4) de su asignación; Alegra devuelve la respuesta guardada del primer intento (`Idempotent-Replayed: true`), que puede estar desactualizada: por eso se vuelve a leer el documento (`GET /invoices/{id}`).
- Una factura `PENDING` con `providerDocumentId` se **consulta** en vez de emitirse de nuevo. Si la consulta falla, queda pendiente (ante la duda no se emite); si Alegra confirma que el documento falló o ya no existe, queda en `ERROR` y puede emitirse otro. Tras un rechazo, el reintento manual descarta el id anterior.

### Cuerpo de la factura
Lo arma `AlegraInvoiceMapper`: cada línea se redondea a 2 decimales (HALF_UP) y los totales son la suma de las líneas ya redondeadas, que es lo que validan Alegra y la DIAN. **El total de la factura puede diferir unos centavos del total del ticket del POS**, que se calculó con otro redondeo. Lleva el bloque `company` completo (con solo el `id` Alegra rechaza), la resolución desde el rango asignado y el cliente de la venta (consumidor final `222222222222` si no hay).

### PDF, XML y correo
- **PDF**: Alegra lo entrega **dentro de `files.zip`** junto al XML del documento adjunto; no hay que generarlo. `GET /invoices/{id}` da un enlace fresco (caduca a los 60 min, nada se guarda). El zip guarda las entradas sin comprimir y con descriptor de datos, así que se lee con `ZipFile`. Se extrae solo la entrada `.pdf`, con tope de 10 MB, y se exige que sea un PDF.
- **XML**: desde `files.xml` (si viene vacío se consulta una vez más), tope de 5 MB y debe ser XML.
- **Descargas**: el enlace del zip redirige al S3 de Alegra. Las redirecciones se siguen a mano (máximo 3) y **cada salto** debe ser `https` de un dominio `alegra.com` o de un bucket de Alegra (`api-alegra-e-provider-*.amazonaws.com`); el token nunca se envía a esos enlaces.
- **Correo** (`POST /send-email`): Alegra adjunta el XML en un zip, no el PDF. Verificado en el sandbox: **entrega a la dirección de `replyTo` y no a la de `to`**, al contrario de su documentación; por eso las dos llevan la dirección del cliente. Confirmar con Alegra y revisar en producción.

### Notas crédito y débito
`AlegraNoteClient`: numeración propia (documentos `91`/`92`, prefijo propio) **sin resolución**; el cuerpo lleva `invoicePeriod` (fecha de la factura de origen) y `associatedDocuments` (fecha, tipo `01`, prefijo, número y CUFE de la factura aceptada). La factura de origen se busca por `(tienda, número legal)`; si no existe o no está validada falla **antes de gastar un número**. El cliente sale de la venta original. Misma disciplina que las facturas (llave, estado legal, rotación tras un rechazo). Los servicios de notas emiten en tres pasos como las facturas (ver «Notas: reserva en tres pasos»).

### Notas: reserva en tres pasos
`CreditNoteServiceImpl` y `DebitNoteServiceImpl` ya no son transaccionales y nunca llaman al proveedor con una transacción abierta ni con el lock de la fila (`V40` agrega `attempts` y `processing_started_at` a `electronic_credit_notes` y `electronic_debit_notes`):
1. **Reservar** (`CreditNoteEmissionTransactions.reserve` / `DebitNoteEmissionTransactions.reserve`, transacción corta con lock por `reference_code`): crea o bloquea la nota; si no está validada ni tiene una reserva vigente (`billing.emission.lease`, 5 min) la deja `PENDING`, cuenta el intento y toma la reserva. Una nota validada o en curso devuelve su estado sin llamar al proveedor. Si dos hilos crean la misma referencia a la vez, el segundo reintenta la reserva y ya la encuentra.
2. **Llamar al proveedor sin transacción**.
3. **Registrar el resultado** (`complete`, transacción corta): vuelve a bloquear la fila, aplica el resultado y libera la reserva. Una respuesta incompleta (por ejemplo, validada sin número legal) deja la nota en `ERROR` en vez de revertirla.

Una reserva abandonada (el proceso murió entre los pasos) la retoma la siguiente solicitud pasado el lease. Las notas **no** tienen reintento automático ni conciliación por `providerDocumentId`: un resultado `PENDING` del proveedor se registra como `ERROR` y se reintenta con una nueva solicitud con la misma referencia. `createFromSale` y el borrado de notas sin validar tampoco abren transacción alrededor de la llamada al proveedor.

---

## Flujo de habilitación (negocio)

> Con Alegra el alta se hace como se describe en «Alta de una empresa en Alegra»; los estados son los de RN-BILL-006.
> **Alegra no pide documentos** (RUT, cámara de comercio): la habilitación no depende de los documentos del local.

El proveedor habilita a la **plataforma**, que compra una bolsa de documentos y la reparte entre
sus locales. Cada local es una empresa asociada bajo el token de plataforma:

1. El administrador crea su cuenta, verifica el correo y registra su local. Desde ese momento **puede vender
   sin facturación electrónica**.
2. Si quiere facturar, usa el asistente (`/api/v1/billing/setup`, ver «Asistente de habilitación»): al guardar sus
   datos fiscales el local pasa a `IN_SETUP` en producción sin subir documentos y el sistema crea su empresa en el
   proveedor (`POST /api/v1/billing/activation/request` sigue existiendo y solo lo deja en `IN_SETUP`).
3. El dueño asocia a Alegra como proveedor tecnológico en el portal de la DIAN y escribe en el asistente el
   TestSetId que le entrega la DIAN; el sistema lo envía y consulta la habilitación.
4. Con la DIAN en `AUTHORIZED`, el asistente importa sus resoluciones de numeración y crea los rangos de notas.
   El primer rango que registra un local sin rango configurado queda elegido; el administrador puede elegir otro
   (`POST /api/v1/billing/numbering-ranges/{id}/set-default`) y la siguiente factura ya lo usa.
5. Con la habilitación de la DIAN y una numeración de facturas utilizable el local pasa solo a `ACTIVE` (actor
   `system`) y emite facturas electrónicas. El SUPER_ADMIN ya no carga nada: monitorea, suspende y reactiva.

---

## Reglas de Negocio

### RN-BILL-001 — Idempotencia de Emisión
- Si una venta ya cuenta con una factura electrónica en estado `VALIDATED`, no se reenvía al proveedor ni se
  duplica la solicitud ante la DIAN (guard bajo bloqueo pesimista sobre `electronic_invoices`).

### RN-BILL-002 — Desacoplamiento y No Bloqueo Local
- El fallo de conexión, timeout o indisponibilidad de la API externa del proveedor nunca revierte la transacción
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
| `VALIDATED` | Factura validada exitosamente por el proveedor y la DIAN (contiene CUFE, QR y PDF) |
| `REJECTED` | Rechazada por la DIAN por inconsistencias en los datos fiscales (HTTP 400 / 422) |
| `ERROR` | Error imprevisto, local sin facturación habilitada, o sin rango de numeración configurado |

### RN-BILL-004 — (Retirada) Cache de Token OAuth2 de Factus
- Regla propia del adaptador de Factus, que fue retirado. Con Alegra no hay token por local: el de plataforma sale de una variable de entorno.

### RN-BILL-005 — Acceso por Local, sin Respaldo Global
- Cada llamada al proveedor usa exclusivamente la empresa (`company.id`) del local correspondiente.
- **No existe fallback a la empresa de otro local.** Si el local no está `ACTIVE` o le falta el `company.id` del entorno, se lanza `BillingNotEnabledException` y no se hace ninguna llamada al proveedor.
- El token de Alegra es de **plataforma** (una variable de entorno por entorno) y el aislamiento entre locales es por `company.id` + `TenantContext`, nunca por token; ver «Modelo de cuentas».

### RN-BILL-006 — Ciclo de Habilitación por Local
```
NOT_REQUESTED → IN_SETUP → ACTIVE ⇄ SUSPENDED
                    │                  ↑
                    └──────────────────┘
```
| Estado | Quién lo produce | Significado |
|---|---|---|
| `NOT_REQUESTED` | (por defecto, sin fila en BD) | El local vende sin facturación electrónica |
| `IN_SETUP` | ADMINISTRATOR del local | Habilitación en curso: datos fiscales, habilitación ante la DIAN y numeración. No pide documentos |
| `ACTIVE` | El asistente (actor `system`) o el SUPER_ADMIN | Empresa del local configurada en el proveedor, habilitada por la DIAN y con numeración de facturas; el local ya puede facturar |
| `SUSPENDED` | SUPER_ADMIN | Facturación pausada (exige motivo), desde `IN_SETUP` o `ACTIVE`. Reactivable a `ACTIVE` |

- No se puede saltar pasos: cada transición se valida contra `BillingActivationStatus.canTransitionTo(...)`.
  Los estados se validan solo en Java: `V42` retiró el `CHECK` de `store_billing_config.status` y llevó a
  `IN_SETUP` los locales que estaban en los estados retirados (`DOCUMENTS_SUBMITTED`, `AWAITING_PROVIDER`, `REJECTED`).
- Pasar a `ACTIVE` (desde `IN_SETUP` o al reactivar desde `SUSPENDED`) exige, en el entorno del local: (1) la cuenta
  configurada (`isAccountConfigured`: token de plataforma del entorno y `company.id` del local; el mensaje dice cuál
  falta), (2) la habilitación de la DIAN en `AUTHORIZED` (`readEnablement`; mensaje distinto si no se inició, si
  está pendiente o si la DIAN respondió otra cosa) y (3) un rango de facturas utilizable (`hasUsableInvoiceRange`:
  activo, vigente y con cupo). La habilitación se consulta al proveedor **antes** de abrir la transacción corta que
  valida y guarda el cambio (`transition` no es transaccional; nunca llamarlo dentro de otra transacción).
  `SUSPENDED` exige `reason`; al salir de ese estado el motivo se limpia.
- **Cambio de entorno** (`switchEnvironment`): en cualquier estado, el entorno destino necesita la cuenta configurada
  y un rango de facturas utilizable. Si el entorno cambia se notifica al local (`BILLING_STATUS_CHANGED`, enlace
  `/invoicing?tab=activation`) diciendo cuál quedó activo; en `SANDBOX` las facturas son de prueba.
- `requestActivation` (ADMINISTRATOR): `NOT_REQUESTED` → `IN_SETUP`; repetirla en `IN_SETUP` no cambia nada; desde
  `ACTIVE` o `SUSPENDED` responde error.
- Pasar a `ACTIVE` o `SUSPENDED` notifica al local (`BILLING_STATUS_CHANGED` vía `NotificationFacade`, enlace
  `/invoicing?tab=activation`). Pasar a `IN_SETUP` no notifica.

### RN-BILL-007 — (Retirada) Cifrado de Secretos en Reposo
- Regla de las credenciales de Factus. `V41` las borró y se eliminaron `SecretCryptoConverter` y la clave `BILLING_ENCRYPTION_KEY`: con Alegra no hay secretos por local en la base.
- Ningún DTO de salida expone el `company.id`: `BillingActivationDto` solo informa si cada entorno está configurado (`sandboxConfigured` / `productionConfigured`, `sandboxCompanyIdConfigured` / `productionCompanyIdConfigured`).

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
   factura ya lo usa, porque se lee de ahí en cada emisión. Al crear un rango, si el
   entorno activo aún no tiene rango, el nuevo queda elegido (con su propia entrada `SET_DEFAULT`).

> Los guardas 1, 2 y 4 dependen del local en contexto (`TenantContext`). Sin local identificado se omiten
> (caso de tests que invocan el servicio directamente); el guarda 3 aplica siempre. `set-default` sí exige local:
> sin él responde `BillingNotEnabledException` (409).

### RN-BILL-009 — Propagación de Errores del Proveedor
Las operaciones sobre el proveedor ya **no devuelven listas vacías ni `false` silenciosos**. Se traduce el fallo real:

| Situación | Excepción | HTTP |
|---|---|---|
| Local sin facturación habilitada / sin rango | `BillingNotEnabledException` | 409 Conflict |
| El proveedor objetó los datos enviados (4xx, no auth) | `AlegraApiException` (extiende `BillingProviderException`) | 422 Unprocessable Content (`HttpStatus.UNPROCESSABLE_CONTENT`) |
| El proveedor rechazó el acceso (401/403) o no responde (5xx, timeout) | `AlegraApiException` (extiende `BillingProviderException`) | 502 Bad Gateway |
| El proveedor activo no implementa la operación | `UnsupportedOperationException` | 501 Not Implemented |

El mensaje del proveedor se propaga al cliente (truncado a 500 caracteres) para que el usuario sepa qué corregir.

### RN-BILL-010 — Idempotencia y Aislamiento de Notas Crédito
- Si una referencia (`reference_code`) ya cuenta con una nota crédito en estado `VALIDATED`, no se reenvía al proveedor ni se duplica ante la DIAN (guard bajo bloqueo pesimista `findByReferenceCodeForUpdate` sobre `electronic_credit_notes`).
- `reference_code` es `UNIQUE` global: si la referencia pertenece a **otro local** se responde 409 sin llamar al proveedor ni exponer la nota existente (mismo criterio que RN-BILL-014).
- Toda operación (emisión, consulta, descarga) exige el `storeId` de la solicitud (`TenantContext`); sin local asociado responde 409. **No hay respaldo al local 1** (antes se asumía).

### RN-BILL-011 — (Retirada) Recuperación Automática de Conflicto 409
- Regla del adaptador de Factus (borrar el borrador pendiente y reintentar una vez). Se retiró junto con Factus y con los endpoints `DELETE …/reference/{referenceCode}` de notas; Alegra no tiene ese conflicto (la `Idempotency-Key` evita el duplicado).

### RN-BILL-012 — Emisión de Nota Crédito a partir de Venta
- Para emitir nota crédito referenciando una venta (`createFromSale`), la venta debe tener factura electrónica `VALIDATED` **en el local actual**. La nota crédito referencia el número de factura legal (`legal_number`).
- `reference_code` determinista: `NC-<número de factura de la venta>-C<concepto>`. Un doble clic o un reintento reutiliza la misma nota en vez de emitir otra ante la DIAN.
- `sale_id` se persiste al crear la nota (antes se asignaba por reflexión y quedaba nulo).

### RN-BILL-016 — Referencia Determinista de Facturas
- El `reference_code` que se envía al proveedor es `<invoiceNumber de la venta>-<saleId>`, **estable entre reintentos** (antes llevaba `System.currentTimeMillis()` y cada reintento tras un timeout podía generar un segundo documento ante la DIAN). Lo mismo para el `code_reference` de ítems sin código.
- Facturar sin local asociado a la venta falla (`IllegalStateException`); no hay respaldo al local 1.

### RN-BILL-017 — Proveedor Configurado
- `billing.provider` (`alegra` | `mock`; por defecto `alegra`) debe corresponder a un adaptador registrado. Si no existe, **la aplicación no arranca** (`BillingProviderStartupValidator`) y el error lista los proveedores disponibles; si aun así una operación lo resolviera en tiempo de ejecución, también falla con ese listado: no hay respaldo silencioso a `mock`, que emitiría facturas falsas por una errata de configuración.
- `MockBillingAdapter` solo se registra con `billing.provider=mock` (`@ConditionalOnProperty`).
- **Puerto de cuenta (`BillingAccountProvider`)**: todo adaptador (`ElectronicInvoicingProvider`) implementa además `isAccountConfigured(storeId, env)`, `verifyConnection(storeId, env)`, `onAccountChanged(storeId)` y `displayName()`. `StoreBillingConfigServiceImpl` y `BillingActivationMapper` obtienen el proveedor activo a través de `ActiveBillingProvider` (activación a `ACTIVE`, cambio de entorno, prueba de conexión, invalidación de caché tras cargar credenciales, flags `sandboxConfigured`/`productionConfigured`) y no dependen de la clase de un adaptador concreto. El mock da la cuenta siempre por configurada y la prueba de conexión por exitosa.
- `BillingProviderException` es la base común de los errores de un proveedor (`AlegraApiException` la extiende). `GlobalExceptionHandler` aplica el mapeo de RN-BILL-009 a la base y responde **501 Not Implemented** a una `UnsupportedOperationException` (operación no implementada por el proveedor activo).

### RN-BILL-018 — Timeouts de Salida
- Todos los clientes HTTP hacia Alegra (`AlegraClientHelper`) se crean con `OutboundHttp.restClient()`: conexión 5 s, lectura 45 s. Un proveedor lento ya no deja hilos ni filas bloqueadas indefinidamente.

### RN-BILL-019 — Líneas de Servicio en Facturas y Notas Crédito
- Una línea de venta de **servicio** (`SaleItemDto.itemType = SERVICE`, ver `sales/spec.md` RN-SALE-004) se factura con el `unit_measure_code` y el `unspsc_code` que **la venta guardó** al momento de cobrar (copiados del catálogo de `services`). Billing no consulta el catálogo de servicios: así editar o desactivar el servicio no cambia lo ya facturado, y no depende de `ServiceFacade`.
- Sin esos datos se usan los valores por defecto (unidad `94`, estándar `999`). Las líneas de **producto** siguen resolviendo el UNSPSC desde `ProductFacade`.
- La nota crédito (`createFromSale`) usa los mismos códigos de la línea, para que coincida con la factura que corrige.
- Pendiente: los códigos de unidad de medida para servicios por hora u otras medidas deben validarse contra el catálogo del proveedor; hoy la tienda los escribe en el servicio sin verificación.

### RN-BILL-020 — Un Vendedor Solo Ve las Facturas de sus Ventas
- Para el rol `SELLER`, `GET /billing/invoices` lista solo las facturas de las ventas que él registró (consulta nativa sobre `sales.user_id`, con orden fijo por `id` descendente; el parámetro `sort` se ignora). Consultar, descargar el PDF o XML y enviar por correo la factura de una venta ajena responde como inexistente. `ADMINISTRATOR` y `SUPERVISOR` ven todas las del local. Ver `sales/spec.md`, RN-SALE-005.

### RN-BILL-021 — Factura de una Venta a Fiado
- La factura electrónica es opcional también en el fiado: solo se emite si quien vende lo pidió (`sendElectronicInvoice`), como en cualquier venta. Cuando la venta es `CREDIT`, `BillingCanonicalMapper` la arma a **crédito**: `payment_form = "2"`, `payment_method_code = "1"` (instrumento no definido) y `payment_due_date` con la fecha límite del fiado (`CreditFacade.findChargeDueDate(storeId, saleId)`, 30 días desde la venta si faltara). El resto de ventas siguen saliendo de contado (`payment_form = "1"`). Ver `credit/spec.md`, RN-CRE-005.
- El municipio del cliente se envía siempre con los 5 dígitos del código DANE: `customers.municipality_id` es numérico y guarda `05001` (Medellín) como `5001`, así que `BillingCanonicalMapper` lo completa con `%05d`.

### RN-BILL-013 — Notas Débito solo con Referencia a Factura
- Solo se emiten notas débito con `customization_id=30` (con referencia a una factura electrónica). El tipo `32` (sin referencia) no está soportado y `electronic_debit_notes.bill_number` es `NOT NULL`, así que se rechaza con 400.
- Conceptos de corrección DIAN para notas débito (`correction_concept_code`): `1` Intereses, `2` Gastos por cobrar, `3` Cambio del valor, `4` Otros.

### RN-BILL-014 — Idempotencia y Aislamiento de Notas Débito
- Guard bajo bloqueo pesimista (`findByReferenceCodeForUpdate` sobre `electronic_debit_notes`): una referencia ya `VALIDATED` devuelve la nota existente sin reenviarla al proveedor; una en `ERROR`/`REJECTED` se reintenta sobre el mismo registro (`prepareForResend`).
- `reference_code` es `UNIQUE` global: si la referencia pertenece a **otro local**, o al mismo local pero para **otra factura**, se responde 409 sin llamar al proveedor ni exponer la nota existente.
- Toda operación (emisión, consulta, descarga) exige el `storeId` de la solicitud (`TenantContext`); sin local asociado se responde 409. No hay respaldo a un local por defecto.

### RN-BILL-015 — Nota Débito sobre la Factura de una Venta
- `POST /debit-notes/sales/{saleId}` exige que la venta tenga factura electrónica `VALIDATED` **en el local actual** (404 si no existe, 409 si no está validada). El `bill_number` se toma de esa factura (si el cliente lo envía, debe coincidir; si no, 400) y la nota queda vinculada a la venta (`sale_id`).
- A diferencia de la nota crédito, los ítems los define el cliente: la nota débito cobra un valor adicional (intereses, gastos), no replica la venta.

---

## API Endpoints

### 1. Facturas Electrónicas de Ventas (`BillingInvoiceController`)
- `GET /api/v1/billing/sales/{saleId}` — consultar estado y datos de la factura de una venta. Solo lee: responde 404 si la emisión todavía no creó la factura (el POS la muestra como «En proceso»).
- `POST /api/v1/billing/sales/{saleId}/retry` — reintentar facturación fallida (`ERROR` o `PENDING`).
- `POST /api/v1/billing/sales/{saleId}/send-email` — enviar ZIP con PDF y XML legal al email indicado. Con Alegra lo envía Alegra y adjunta el XML (no el PDF).
- `GET /api/v1/billing/sales/{saleId}/pdf` — descargar el PDF legal de la factura.
- `GET /api/v1/billing/sales/{saleId}/xml` — descargar el XML legal de la factura.
- `GET /api/v1/billing/invoices` — historial paginado de facturas del local, filtrable por estado.

### 2. Rangos de Numeración DIAN (`BillingNumberingRangeController`)
> Con Alegra estos endpoints trabajan sobre los rangos locales (ver «Numeración local»).

- `GET /api/v1/billing/numbering-ranges/dian` — resoluciones que la DIAN tiene para el NIT del local (con Alegra,
  `listResolutions` con el NIT de sus datos fiscales; sin `id` porque aún no son rangos registrados). En el entorno de
  pruebas responde una lista vacía; en producción sin datos fiscales responde 409.
- `GET /api/v1/billing/numbering-ranges` — rangos registrados para el local.
- `GET /api/v1/billing/numbering-ranges/{id}` — detalle de un rango (`null` → 404 si no existe).
- `POST /api/v1/billing/numbering-ranges` — registrar una resolución DIAN. Body: `document`, `prefix`,
  `resolution_number`, `current`; con Alegra también el rango `from`/`to`, la vigencia y la clave técnica (ver «Numeración local»).
- `DELETE /api/v1/billing/numbering-ranges/{id}` — eliminar. El rango se marca como eliminado (`deleted`)
  en lugar de borrarse.
- `PATCH /api/v1/billing/numbering-ranges/{id}/toggle-status` — alternar activo/inactivo. Devuelve el rango actualizado.
- `PATCH /api/v1/billing/numbering-ranges/{id}/current` — actualizar el consecutivo (solo hacia adelante).
- `POST /api/v1/billing/numbering-ranges/{id}/set-default` — usar este rango para facturar en el entorno activo
  del local. Devuelve el rango. 404 si no existe en la cuenta del local, 409 si está inactivo o vencido.

### 3. Habilitación — Back Office (`StoreBillingConfigController`, SUPER_ADMIN)
- `GET /api/v1/backoffice/billing?status=IN_SETUP` — locales en una etapa (seguimiento).
- `GET /api/v1/backoffice/stores/{storeId}/billing` — estado del local (sin secretos).
- `PUT /api/v1/backoffice/stores/{storeId}/billing/company-id?environment=SANDBOX|PRODUCTION` — (Alegra) guarda el
  `company.id` del local en ese entorno. Body `{ "companyId": "…" }` (máx. 64 caracteres). Idempotente; solo SUPER_ADMIN;
  la respuesta indica si está cargado, no su valor.
- `POST /api/v1/backoffice/stores/{storeId}/billing/test-connection?environment=…` — lee la empresa del local en Alegra. No activa nada. Nunca lanza error por rechazo: devuelve `{success, environment, message}`.
- `PATCH /api/v1/backoffice/stores/{storeId}/billing/environment?environment=…` — cambiar entorno activo. En cualquier
  estado exige la cuenta y un rango de facturas utilizable en el destino; notifica al local (RN-BILL-006).
- `POST /api/v1/backoffice/stores/{storeId}/billing/transition` — cambiar etapa. Body: `{status, reason}`.

### 4. Habilitación — Local (`BillingActivationController`)
- `GET /api/v1/billing/activation/status` — estado de facturación del local autenticado (sin credenciales).
- `GET /api/v1/billing/activation/summary` — para el POS, cualquier rol del local (también `SELLER`): `{ active, environment }`. El cobro solo ofrece la factura electrónica si `active` y marca «Modo prueba» en `SANDBOX`.
- `POST /api/v1/billing/activation/request` — empezar la habilitación (ADMINISTRATOR): `NOT_REQUESTED` → `IN_SETUP`. No pide documentos; idempotente en `IN_SETUP` (RN-BILL-006).

### 4b. Asistente de habilitación (`BillingSetupController`)
Detalle en «Asistente de habilitación». El local sale siempre de la solicitud (`TenantContext`); sin local, 409.
- `GET /api/v1/billing/setup` — avance paso a paso (ADMINISTRATOR, SUPERVISOR). Puede activar el local si ya cumple todo.
- `PUT /api/v1/billing/setup/fiscal-profile` — guardar los datos del RUT y registrar la empresa en producción (ADMINISTRATOR).
- `POST /api/v1/billing/setup/dian-enablement` — enviar el TestSetId de la DIAN (ADMINISTRATOR).
- `POST /api/v1/billing/setup/numbering/import` — importar las resoluciones de la DIAN y crear los rangos de notas (ADMINISTRATOR).
- `GET /api/v1/backoffice/stores/{storeId}/billing/setup` — el mismo avance para un local, desde el back office (SUPER_ADMIN, `BackofficeBillingSetupController`).
- `GET /api/v1/billing/catalogs/divipola` — departamentos y municipios DANE (SUPER_ADMIN y cualquier rol de local, `BillingCatalogController`, `Cache-Control: max-age=86400`).

### 5. Notas Crédito Electrónicas (`BillingCreditNoteController`)
- `POST /api/v1/billing/credit-notes/validate` — crear y validar nota crédito ante Alegra y la DIAN.
- `POST /api/v1/billing/credit-notes/sales/{saleId}?conceptCode=2&observation=` — emitir nota crédito para anular o ajustar una venta facturada (`conceptCode` por defecto `2` = anulación).
- `GET /api/v1/billing/credit-notes?status=` — historial paginado del local, filtrable por estado.
- `GET /api/v1/billing/credit-notes/{id}` — consultar nota crédito por ID local.
- `GET /api/v1/billing/credit-notes/reference/{referenceCode}` — consultar nota crédito por código de referencia POS.
- `GET /api/v1/billing/credit-notes/{id}/pdf` — descargar PDF legal de la nota crédito (Base64 decodificado como `application/pdf`).
- `GET /api/v1/billing/credit-notes/{id}/xml` — descargar XML legal de la nota crédito (Base64 decodificado como `application/xml`).

### 6. Notas Débito Electrónicas (`BillingDebitNoteController`)
- `POST /api/v1/billing/debit-notes/validate` — crear y validar nota débito ante Alegra. Responde 200 con el `status` resultante (`VALIDATED`, `REJECTED`, `ERROR`).
- `POST /api/v1/billing/debit-notes/sales/{saleId}` — emitir nota débito sobre la factura de una venta (RN-BILL-015). Body: `CreateDebitNoteRequest`.
- `GET /api/v1/billing/debit-notes?status=` — historial paginado del local, filtrable por estado.
- `GET /api/v1/billing/debit-notes/{id}` — consultar nota débito por ID local (404 si no existe en el local).
- `GET /api/v1/billing/debit-notes/reference/{referenceCode}` — consultar por código de referencia.
- `GET /api/v1/billing/debit-notes/{id}/pdf` — PDF legal (`application/pdf`). 404 si no existe, 409 si no está validada.
- `GET /api/v1/billing/debit-notes/{id}/xml` — XML legal (`application/xml`). 404 si no existe, 409 si no está validada.

---

## Modelo de Datos

### Tabla: `electronic_invoices`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGSERIAL | PK, Auto-increment |
| store_id | BIGINT | NOT NULL, FK → stores(id) (desde `V13`) |
| sale_id | BIGINT | UNIQUE, NOT NULL, FK → sales(id) |
| legal_number | VARCHAR(50) | NULLABLE (antes `factus_number`, renombrada en `V41`) |
| cufe | VARCHAR(255) | NULLABLE |
| qr_code | TEXT | NULLABLE |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' |
| error_message | TEXT | NULLABLE |
| pdf_url | VARCHAR(500) | NULLABLE |
| validated_at | TIMESTAMP | NULLABLE |
| provider_document_id | VARCHAR(64) | NULLABLE (`V38`): id del documento en el proveedor; también en las notas |
| attempts | INT | NOT NULL, DEFAULT 0 (`V39`): intentos desde el último reinicio manual |
| processing_started_at | TIMESTAMP | NULLABLE (`V39`): reserva (lease) de la emisión en curso |
| next_retry_at | TIMESTAMP | NULLABLE (`V39`): cuándo puede reintentarla el job; índice `(status, next_retry_at)` |
| environment | VARCHAR(12) | NULLABLE (`V42`): entorno con el que se emitió (`SANDBOX` = de prueba). Se anota al reservar la emisión con el entorno del local, solo si aún no tiene; las anteriores a `V42` tomaron el entorno actual de su local |
| created_at / updated_at | TIMESTAMP | NOT NULL |

### Tabla: `store_billing_config` (`V14__Store_Billing_Config.sql`)
Una fila por local (relación 1 a 1 con `stores`).

| Campo | Tipo | Restricciones |
|---|---|---|
| store_id | BIGINT | PK, FK → stores(id) |
| status | VARCHAR(30) | NOT NULL, DEFAULT 'NOT_REQUESTED'. Sin CHECK desde `V42`: se valida en Java (RN-BILL-006) |
| status_reason | VARCHAR(500) | NULLABLE (motivo de la suspensión) |
| environment | VARCHAR(12) | NOT NULL, DEFAULT 'SANDBOX', CHECK IN ('SANDBOX','PRODUCTION') |
| sandbox_numbering_range_id | INT | NULLABLE |
| production_numbering_range_id | INT | NULLABLE |
| updated_by | VARCHAR(100) | NULLABLE (usuario que hizo el último cambio) |
| created_at / updated_at | TIMESTAMP | NOT NULL |

Índice: `IDX_store_billing_config_status` (cola de revisión del back office).

### Tabla: `billing_company_profile` (`V37__Billing_Self_Service.sql`)
Datos fiscales del local (los de su RUT) con los que se crea y actualiza su empresa en el proveedor. Una fila por local.

| Campo | Tipo | Restricciones |
|---|---|---|
| store_id | BIGINT | PK, FK → stores(id) |
| legal_name | VARCHAR(450) | NOT NULL (razón social) |
| trade_name | VARCHAR(450) | NULLABLE (nombre comercial) |
| nit | VARCHAR(30) | NOT NULL |
| dv | VARCHAR(1) | NOT NULL (dígito de verificación) |
| identification_type | VARCHAR(2) | NOT NULL, DEFAULT '31' (NIT) |
| organization_type | INT | NOT NULL (1 jurídica, 2 natural) |
| regime_code | VARCHAR(200) | NOT NULL |
| economic_activity | VARCHAR(4) | NULLABLE (código CIIU) |
| email | VARCHAR(255) | NOT NULL |
| phone | VARCHAR(30) | NULLABLE |
| address | VARCHAR(255) | NOT NULL |
| city_code | VARCHAR(5) | NOT NULL (código DANE del municipio) |
| department_code | VARCHAR(2) | NOT NULL (código DANE del departamento) |
| created_at / updated_at | TIMESTAMP | NOT NULL |

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
| debit_note_number | VARCHAR(50) | NULLABLE (número legal asignado por la DIAN) |
| cude | VARCHAR(255) | NULLABLE |
| qr_code | TEXT | NULLABLE |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' |
| error_message | TEXT | NULLABLE |
| pdf_url | VARCHAR(500) | NULLABLE |
| validated_at | TIMESTAMP | NULLABLE |
| created_at / updated_at | TIMESTAMP | NOT NULL |

Índices: `IDX_electronic_debit_notes_store`, `IDX_electronic_debit_notes_store_status`, `IDX_electronic_debit_notes_bill`.

### Tablas de Alegra (`V33__Alegra_Local_Numbering.sql`)
| Tabla | Contenido |
|---|---|
| `billing_numbering_range` | Rangos por local, entorno y documento (`01`, `91`, `92`): `prefix`, `resolution_number`, `min_number`, `max_number`, `next_number`, vigencia, `technical_key`, `active`, `deleted`. Único `(store_id, environment, document, prefix, resolution_number)` |
| `billing_document_number` | Libro de asignaciones: `reference_code`, `range_id`, `prefix`, `number`, `idempotency_key`, `completed`. Único `(store_id, environment, document, reference_code)` y `(range_id, number)` |
| `store_billing_config` (columnas nuevas) | `sandbox_company_id`, `production_company_id` (VARCHAR(64)): `company.id` de Alegra por entorno |
| `electronic_invoices`, `electronic_credit_notes`, `electronic_debit_notes` (columna nueva) | `provider_document_id` |

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
| Consultar rangos DIAN | - | ✓ | ✓ | - |
| Crear / eliminar / activar rango | - | ✓ | - | - |
| Actualizar consecutivo | - | ✓ | - | - |
| Elegir rango para facturar | - | ✓ | - | - |

### Habilitación de facturación
| Operación | SUPER_ADMIN | Admin Local | Supervisor |
|---|---|---|---|
| Ver estado de facturación del propio local | - | ✓ | ✓ |
| Solicitar activación | - | ✓ | - |
| Ver el avance del asistente | ✓ (cualquier local, back office) | ✓ | ✓ |
| Datos fiscales, set de pruebas DIAN, importar numeración (asistente) | - | ✓ | - |
| Ver cola de revisión / estado de cualquier local | ✓ | - | - |
| Cargar credenciales / probar conexión | ✓ | - | - |
| Cargar el `company.id` del proveedor (Alegra) | ✓ | - | - |
| Cambiar etapa o entorno | ✓ | - | - |

> El administrador del local **nunca** ve ni envía credenciales: ningún endpoint bajo `/api/v1/billing/activation`
> las acepta ni las devuelve.

---

## Pendiente / no verificado

- **Despliegue**: las migraciones de facturación van de V38 a V44, después de las V33–V37 que `main` usa para otros módulos. Esta rama se despliega solo después de unirla con `main` (sin sus V33–V37, Flyway rechaza arrancar sobre una base que ya las aplicó). Ver [preproducción](preproduction.md).
- **Producción real**: token, empresa habilitada, resoluciones/numeración y ensayo controlado de factura, notas, archivos y correo pendientes. Los tests HTTP simulados no sustituyen esa verificación.
- **Documentos históricos**: contexto fiscal desconocido requiere asociación verificada; no hay backfill especulativo. Una asignación antigua con `completed=true` no demuestra aceptación.
- **Notas**: existe recuperación explícita por `POST /{id}/retry`, conciliación por identificador y estado PENDING conservado. No hay job automático de notas ni webhook. Un rechazo que necesita cambios se corrige explícitamente, sin alterar una solicitud incierta.
- **Alta de empresa incierta**: un recibo guardado se reutiliza. Si el CREATE quedó incierto sin recibo, soporte debe verificar la asociación; no se repite ni se asocia automáticamente por NIT.
- **Correo**: el sandbox entrega a `replyTo`; revisar la entrega real y las notificaciones de la empresa en producción.
- **Descargas**: el timeout de lectura no limita por sí solo la duración total de una descarga lenta. Las notas reutilizan el descargador seguro; falta ensayo real de sus PDF/XML en esta revisión.
- **Precisión**: los mappers preservan el bruto persistido y tienen regresiones de cantidades altas y tasas mixtas. La aceptación real del precio unitario de precisión ampliada en Alegra sigue pendiente.
- **Mejoras posteriores**: alertas de numeración próxima a agotarse, centralización de códigos DIAN y registro de resoluciones adicionales como inactivas durante la importación.
- **Nota de versión V41**: elimina credenciales heredadas de Factus y cambia contratos antiguos; conservar copia restaurable y revisar consumidores antes de actualizar desde una versión anterior.
## Preparación previa al token de producción

La guía [preproduction.md](preproduction.md) distingue pruebas HTTP contractuales de pruebas reales sandbox, define recuperación segura y enumera los bloqueos de despliegue. El asistente del cliente conserva producción; las resoluciones no se consultan en sandbox.

Los documentos nuevos fijan entorno, proveedor y empresa, sin guardar tokens. La configuración actual del local no sustituye ese contexto para consultas o recuperación. Un documento histórico sin contexto verificable requiere revisión; no se infiere su empresa desde el estado actual.

Las solicitudes y recepciones fiscales se guardan por separado del cierre local. Una respuesta incierta no autoriza cambiar la llave ni emitir a ciegas. El alta de empresa usa un registro transaccional por local/entorno/proveedor; una recepción conocida se reutiliza y una creación incierta exige soporte, sin asociación automática solo por NIT.

El mapeo fiscal conserva el importe bruto persistido de cada línea de venta. Las notas automáticas reciben el mismo snapshot; los ajustes manuales mantienen precios antes de impuestos. Una nota fiscal no registra por sí misma un reembolso ni movimientos de inventario.
