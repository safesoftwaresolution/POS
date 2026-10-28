<!-- doc-version: 1.5 | last-updated: 2026-10-24 -->
# Roadmap y Estado por Módulo

Este documento complementa los `spec.md` de cada módulo (que describen el comportamiento *actual*). Aquí se registra lo que **falta**, la **deuda técnica** conocida y las **próximas ideas**, para que quede como referencia entre sesiones de trabajo.

## Estado por módulo

| Módulo | Estado actual | Deuda técnica / faltante | Próximas ideas |
|---|---|---|---|
| auth | JWT access+refresh con rotación y ventana de gracia, auto-registro con verificación de correo (V7), reenvío de verificación y recuperación de contraseña por correo (V19) | Lockout en memoria; sin rate limiting; logout exige token válido; sin caducidad absoluta de sesión; sin `SecurityConfig` test por rol | 2FA opcional para ADMIN |
| users | CRUD por local, `SUPER_ADMIN` no asignable por el local, seed de superadmin solo con `SEED_SUPERADMIN_PASSWORD`, tope de usuarios activos por plan (V22, RN-USER-005) | Listado solo de activos (no se puede reactivar); sin protección del último `SUPER_ADMIN`; no se revocan sesiones al cambiar clave; `PATCH …/password` sin `@PreAuthorize` | Invitación de usuarios por correo |
| categories | Catálogo global + subcategorías por local (V12) | — | Íconos/colores por categoría |
| products | CRUD por local, barcode/código únicos por local, stock con `UPDATE` atómico (`adjustStock`), carga masiva por Excel (vía `inventory`, RF-INV-005), vencimiento opcional por lotes ligeros (V27 `product_lots`, RN-PROD-004/005: stock sin descuento por lote y reparto FEFO derivado) | `updatePurchasePrice` rechaza costo > precio de venta; en PostgreSQL podrían seguir vivos los `UNIQUE` globales en línea de V1 (`products_internal_code_key`/`products_barcode_key`; V9 solo borra `UK_products_*`), igual en `customers.identification` y `suppliers.tax_id`: verificar con `\d products` | Importar también desde CSV; columna de vencimiento/lote en la plantilla Excel; FEFO real por lote en la venta (hoy es un reparto derivado) |
| inventory | Kardex, alerta de stock bajo (notificación + correo), importación masiva y conteo físico desde Excel con vista previa y "todo o nada" (RF-INV-005), movimientos manuales sin proveedor desde Inventario (*Mover stock*: entrada, salida con motivo, conteo y ajuste) y pestaña *Movimientos* del kardex (más reciente primero) | Tipos de movimiento sin enum (`ENTRY`/`ENTRADA`); `AJUSTE` solo suma; alerta antes del commit; `InventoryMapper` atrapa el `EntityNotFoundException` de `UserFacade.getById` pero la transacción ya quedó marcada para rollback (con el fallback `orElse(1L)` y sin ese usuario, el movimiento falla con `UnexpectedRollbackException`) | Conteo por cámara/escáner en el móvil |
| purchases | Compras a proveedor, sube stock, idempotencia | — | Órdenes de compra sugeridas por stock bajo |
| sales | Multi-método de pago, devoluciones, multi-tenant, idempotencia, evento `SaleCompletedEvent` | Método de pago no validado contra el catálogo; stock insuficiente responde 400 (no 409); falta filtro por vendedor; fallback `orElse(1L)` de usuario | Venta a crédito/fiado |
| customers | CRUD por local, "Cliente General" por local | — | Historial de compras por cliente |
| suppliers | CRUD por local | — | — |
| reports | CQRS con `JdbcClient`; incluye cierre de caja (`/cash-closing`), mapa de calor de ventas por día y hora (`/sales-heatmap`, RF-REP-008) y lotes vencidos / próximos a vencer (`/expiring`, RF-REP-007, una fila por lote) | No descuenta devoluciones; rentabilidad usa precios actuales; sin índices (ver abajo) | Rentabilidad por categoría |
| settings | Datos del negocio + catálogos de parámetros + logo | Importa `billing.internal` (`FactusTokenManager`); creación de la fila por defecto a verificar | Impuesto configurable (hoy 19% fijo en frontend) |
| billing | Facturas, notas crédito (V23) y débito (V24), habilitación por local (V14), eventos de dominio, timeouts, referencias deterministas | Factus dentro de la transacción con lock; sin reintento automático/outbox; `PENDING` por fallo temporal se trata como `ERROR`; códigos DIAN hardcodeados; cifrado sin rotación; frontend parcial (`InvoicingBento`: activación y facturas del local; `BillingBento` en back office); sin verificación contra el sandbox real | Alertas de rango por agotarse |
| stores | Onboarding del dueño, KYC, back office, auditoría de estados (V18/V20) y de asignación de dueños (V21), planes por local (V22); spec actualizado | Estados como `String`; documentos KYC con URL pública; `StoreFacade` expone `MultipartFile` | — |
| legal | Documentos legales de plataforma | Spec sin `spec-version` | — |
| support | Tickets PQR/bugs con radicado aleatorio y seguimiento público mínimo | Sin rate limiting; acepta `storeId` del cliente | — |
| notifications | Bandeja por local (V11) | Listado sin paginar | — |
| storage | R2: subida/borrado con tipos permitidos, carpeta por local y rol | Sin timeouts S3; cliente con credenciales falsas si falta R2; KYC en bucket público | URLs prefirmadas |
| backoffice | Staff de plataforma y estadísticas del embudo | Mapeo en el servicio; sin tests | — |

## Auditoría SDD y correcciones (2026-10-16)

Una revisión del arnés, los specs y el código encontró desajustes. **Ya corregido en el código:**

- `updateUser` permitía a un `ADMINISTRATOR` asignarse `SUPER_ADMIN`; un `SUPERVISOR` podía restablecer la clave de un `ADMINISTRATOR`; reactivar al único administrador fallaba.
- `StorageController` sin `@PreAuthorize`, sin validación de tipo y con borrado de cualquier clave del bucket.
- `DatabaseSeeder` sembraba `superadmin` y `admin` con claves conocidas en todos los perfiles.
- CORS reflejaba cualquier origen con credenciales.
- Stock sin bloqueo (read-modify-write) pese a lo que decían README y CLAUDE.md.
- Sin `spring-boot-starter-validation`: los `@Valid` no se aplicaban.
- Seguimiento público de tickets exponía correo, teléfono y descripción, con radicado secuencial en memoria.
- Facturación: sin timeouts, respaldo silencioso a `mock`, respaldo al local 1, notas crédito sin aislar por local, `reference_code` con `currentTimeMillis` (riesgo de documento duplicado ante la DIAN), `sale_id` de la nota crédito nunca persistido, INSERT dentro de una transacción `readOnly`.
- Corregido el asunto del correo de verificación ("cor reo").

**Documentación puesta al día:** specs nuevos de `storage`, `notifications` y `backoffice`; reescritos `auth`, `users`, `stores`; actualizados `billing`, `sales`, `inventory`, `settings`, `support`, `reports`, `products`, `purchases`, `customers`; y los globales `_architecture`, `_conventions`, `_database-schema`, `_deployment`, `_project`, `_security`, `_index`.

## Pendientes priorizados (de la misma auditoría)

**Seguridad / datos**
1. Rate limiting (login por IP, `/register`, `/refresh`, `/support/tickets`) y lockout persistente.
2. Revocar los refresh tokens al cambiar o restablecer la contraseña; hacer público el logout.
3. ~~Frontend: tokens en `localStorage`~~ — resuelto (2026-10-16): `http.js` guarda los tokens solo en memoria; la sesión viaja en cookies `HttpOnly`.
4. Validar al arrancar `JWT_SECRET` (≥ 32 bytes) y `BILLING_ENCRYPTION_KEY`; quitar el perfil `dev` por defecto y H2 del JAR de producción.
5. ~~`docker-compose.prod.yml` no reenviaba `EMAIL_*`, `SMTP_*`, `APP_FRONTEND_URL`, `JWT_COOKIE_SECURE`, `BILLING_PROVIDER`~~ — resuelto (2026-10-16); `JWT_SECRET`, `DB_PASSWORD` y `APP_FRONTEND_URL` son obligatorios.
6. Aislamiento por local automático (`@Filter` de Hibernate o RLS) en vez de manual por servicio; `ProductFacade.updatePurchasePrice` aún no valida el local.
7. Documentos KYC y archivos en bucket privado con URLs prefirmadas.
8. Retirar de git `backend/data_backup_20260824_104444/` (una base H2 con usuarios y hashes) y rotar credenciales si el repo fue compartido.

**Datos / rendimiento**
9. Crear los índices `sales(store_id, created_at)` e `inventory_movements(product_id, created_at)` (`_database-schema.md` los documentaba como existentes y no lo estaban).
10. Zona horaria: la imagen `prod` ya fija `TZ`/`-Duser.timezone=America/Bogota`. Falta `hibernate.jdbc.time_zone` y migrar datos ya guardados en UTC, si los hubiera.
11. Descontar devoluciones en reportes; guardar el costo en `sale_items` para la rentabilidad.

**Facturación** (ver `modules/billing/spec.md`, "Pendiente")
12. Sacar la llamada a Factus de la transacción con lock; reintento automático / outbox; centralizar mapeo DIAN; rotación de claves de cifrado; validar vigencia y agotamiento del rango antes de emitir; frontend de habilitación, rangos y notas.

**Arquitectura**
13. Sacar `FactusTokenManager` de `settings` vía `BillingFacade`; añadir ArchUnit para que la regla de encapsulación se verifique.
14. Decidir la ubicación de la API pública de `billing` (`api/`, `dto/`, `exception/` frente a la raíz) y unificar `CreditNoteDTO` / `Dto`.
15. Mover a mappers: `BackofficeStaffServiceImpl.toStaffDto` y el armado de la solicitud en `CreditNoteServiceImpl.createFromSale`.

**Calidad**
16. ~~CI y Dependabot~~ — añadidos (`.github/workflows/ci.yml`: backend `mvnw verify`, frontend lint + build, `init.sh --skip-tests`; `.github/dependabot.yml`), **sin ejecutarse aún en GitHub**. Pendiente: dependency-check y Testcontainers con PostgreSQL (los tests y la concurrencia corren solo sobre H2).
17. ~~springdoc 2.8.x (Boot 3)~~ — resuelto: springdoc 3.1.1 (Boot 4.1), AWS SDK 2.56, jjwt 0.13, jacoco 0.8.15 y `spring-boot-starter-flyway`; `/v3/api-docs` y Swagger UI verificados. Sigue pendiente el análisis de vulnerabilidades (dependency-check) además de Dependabot.
18. ~~IVA fijo 19% y CUFE inventado en `POSBento.jsx`~~ — resuelto: el total es la suma de precios (IVA incluido), se envía `sendToFactus` y no se muestra un CUFE falso. Sigue pendiente completar la UI de facturación (hoy `InvoicingBento` cubre activación y facturas).
19. Tests de seguridad por rol con `MockMvc`; `V23MigrationSqlTest` (notas crédito); test concurrente del stock.

- **`docker-compose.prod.yml` no reenvía variables nuevas** (`BILLING_PROVIDER`, `EMAIL_*`/`SMTP_*`, `APP_FRONTEND_URL`, `JWT_COOKIE_SECURE`): en producción por compose el correo de verificación quedaría en modo `log` y las cookies sin `Secure`. Decidir y agregarlas. (2026-10-02)
- **README raíz y `frontend/README.md` desactualizados — corregidos 2026-10-02** (roles, módulos, páginas, `.env`, seeds).
- **`.ai/features.json`** sigue en 1.2.0 (última entrada 2026-08-19) y `history.md` no refleja la rama paralela; el módulo `backoffice` tampoco figura como módulo en el harness. No se tocó (el harness tiene su propio protocolo).

- **Frontend (2026-10-16)**: tokens de color y componentes base (`components/ui`), pantalla de usuarios del local, editar/eliminar clientes, activar/desactivar productos, logo vía `/settings/logo`, rutas con guarda por rol y carga diferida, páginas públicas de legal y soporte. Pendiente: `GET /purchases/{id}` sin pantalla de detalle; ~24 avisos `set-state-in-effect` de oxlint (cargas iniciales con `setLoading` dentro de efectos); `InvoicingBento`, `ProductsBento` y `POSBento` ya se dividieron en `components/invoicing|products|pos` (2026-10-16); quedan grandes `CheckoutModal` (≈525), `StoresBento` (≈600), `BillingBento` y `StaffBento` (≈470-500); revisión visual y de contraste (WCAG) en modo claro/oscuro.
- **Reportes por rol** (resuelto): las analíticas están en el Dashboard y muestran a cada rol solo lo que su endpoint permite (`SUPERVISOR` ve el estado de inventario; el resto de reportes y la Caja son solo `ADMINISTRATOR`).

## Valores agregados propuestos (para retail colombiano)

1. **Escaneo dinámico en POS** — *Implementado 2026-09-15* (`POSBento.jsx`).
2. **Subcategorías de tienda** — *Implementado 2026-09-16* (`categories`, `V12`).
3. **Venta a crédito / fiado a clientes** — saldo pendiente por cliente y abonos parciales. Requiere nuevo campo en `sales` o módulo de cuentas por cobrar.
4. **Alertas de stock bajo** — *Implementadas*: notificación en la app y correo a ADMINISTRATOR/SUPERVISOR al cruzar el mínimo (`LowStockAlertNotifier`). Falta la sugerencia de orden de compra hacia `purchases`.
5. **Cierre de caja / arqueo diario** — *Implementado* (`GET /api/v1/reports/cash-closing`, UI en `CashBento` (`/cash`), con el arqueo "efectivo contado vs esperado" calculado en el cliente; no se guarda el cierre).
6. **Impresión térmica directa** (58/80 mm) en vez de `window.print()`.
7. **Envío de tiquete por WhatsApp/correo** al cliente tras la venta.
8. **Modo offline / PWA** (impacto alto, esfuerzo alto: cola local y conflictos con el descuento de stock).
9. **Reportes de rentabilidad por categoría** (`/profitability` ya existe por producto).
10. **Carga masiva de productos** — *Implementada 2026-10-24* desde Excel (.xlsx) con plantilla, vista previa y conteo físico (`inventory`, RF-INV-005). Falta CSV.
11. **Vencimiento de productos** — *Implementado 2026-10-25* con **lotes ligeros**: cada entrada de stock (*Mover stock → Entrada*, *Compra*, stock inicial) puede guardar un lote con vencimiento y código; el stock sigue siendo un solo contador y lo que queda de cada lote se deriva asumiendo que se vende primero lo que vence primero (RN-PROD-005). Etiquetas "Vence en N días" / "Vencido", gestión de lotes al editar un producto (corregir fecha, código y unidades; quitar; asignar fecha a unidades sin ella; los lotes agotados se consolidan para que no reaparezcan), banner y tarjeta "Próximos a vencer" por lote en el Dashboard (`GET /reports/expiring`) e informe en PDF desde Inventario (`GET /reports/expiring/pdf`, 2026-10-28). Pendiente: aviso diario por notificación y correo (será la primera tarea programada del proyecto: hoy no hay `@EnableScheduling`; debe evitar repetir el aviso), columna de vencimiento/lote en la plantilla Excel, ventana de aviso configurable por local, etiqueta en el POS, bloqueo de venta de vencidos y FEFO real por lote en la venta.
