<!-- spec-version: 0.1 (propuesta, sin implementar) | last-updated: 2026-11-20 -->
# Módulo: Businesses (Negocios con varias sucursales) — Fase 1

> **Estado: U1 a U6 implementadas (fase 1 completa)** (migraciones `V45` y `V46`, módulo `businesses`, endpoints de back office, cambio de sucursal, `GET /business/me`, alta de sucursales por el dueño, disolución del negocio, facturación con NIT compartido y estadísticas consolidadas). y frontend (selector de sucursal, `/business`, `/backoffice/businesses`). Fase 2 (catálogo compartido con stock por sucursal) queda fuera y solo se reserva el campo de configuración.
>
> Ajustes respecto al diseño inicial: `refresh_tokens.store_id` está en la migración `V46` (U2); el cambio de sucursal es `POST /api/v1/auth/switch-store` (no `/business/switch`), porque emite las cookies y vive en `auth`; `PATCH …/billing-mode` pasa a U4, porque el bloqueo por facturas `VALIDATED` necesita `billing`; las sucursales nuevas **siempre** pasan por revisión del `SUPER_ADMIN` (pregunta 1 resuelta).

## Dependencias
- **Requiere**: `stores` (cada sucursal es un `store`), `users` (el dueño), `auth` (cambio de sucursal), `billing` (modo de NIT), `sales` (estadísticas, lectura nativa).
- **Requerido por**: frontend (selector de sucursal y panel consolidado).

## Bounded Context
Agrupa varios locales (`stores`) de un mismo dueño bajo un **negocio**, deja al dueño ("admin padre") cambiar de sucursal y ver estadísticas consolidadas. **No cambia el aislamiento por tenant**: cada petición sigue operando sobre un solo `store_id` (`TenantContext`). Un local sin negocio se comporta exactamente como hoy.

## Decisiones de diseño
1. **Sucursal = `store`.** Todas las tablas ya tienen `store_id`; no se re-escopa nada. Se añade `stores.business_id` (nullable).
2. **Configuración por negocio**, fijada por `SUPER_ADMIN` al activar el módulo:
   - `billing_mode`: `SEPARATE_TAX_ID` (cada sucursal con su empresa de Alegra y sus rangos, como hoy) o `SHARED_TAX_ID` (todas facturan con la empresa y los rangos de la sucursal principal).
   - `catalog_mode`: `INDEPENDENT` (única opción aceptada en fase 1; `SHARED` responde `400` "no disponible aún"). Se guarda ya para no migrar la tabla en fase 2.
3. **El cambio de sucursal emite un JWT nuevo** con el `storeId` elegido. Las lecturas multi-local existen solo en los endpoints de estadísticas consolidadas, de solo lectura.
4. **Solo el dueño (`ADMINISTRATOR`) cruza sucursales.** `SUPERVISOR` y `SELLER` siguen atados a un local.

## Roles y Permisos
- `SUPER_ADMIN`: crea el negocio, elige modos, asigna dueño y sucursal principal, agrega o quita sucursales, fija `max_branches` (sucursales adicionales a la principal).
- `ADMINISTRATOR` dueño del negocio: lista sus sucursales, cambia de sucursal, crea sucursales nuevas (hasta `max_branches` adicionales a la principal), ve las estadísticas consolidadas.
- Otros roles: sin acceso a `/api/v1/business/**`.

## Modelo de Datos (migración `V45`)
| Tabla | Cambio |
|---|---|
| `businesses` (nueva) | `id`, `name`, `owner_user_id` (FK users), `main_store_id` (FK stores), `billing_mode`, `catalog_mode`, `max_branches`, `created_at`. Los modos son `VARCHAR` **sin `CHECK … IN`** (bug de H2, ver CLAUDE.md); se validan con enum en Java. |
| `stores` | `business_id BIGINT NULL` + FK + índice. |
| `business_audit` (nueva) | Historial inmutable sin FKs: `action` (`CREATED`, `UPDATED`, `BRANCH_ADDED`, `BRANCH_REMOVED`, `BRANCH_CREATED`, `DISSOLVED`, `BILLING_MODE`), `affected_store_id`, `detail`, `performed_by`. |
| `refresh_tokens` | `V46` (U2): `store_id BIGINT NULL`, sin FK, sucursal activa de esa sesión (ver RN-BIZ-004). |

Constraint: un `store` pertenece a un solo negocio; un usuario es dueño de un solo negocio (`UNIQUE(owner_user_id)`).

## Reglas de Negocio
- **RN-BIZ-001 (Pertenencia)**: el negocio se resuelve siempre desde el `storeId` del JWT y se verifica que el usuario sea `owner_user_id`. **Nunca** se acepta un `businessId` o `storeId` del cliente como fuente de autorización.
- **RN-BIZ-002 (Sucursal principal)**: `main_store_id` es la fuente de la facturación en modo `SHARED_TAX_ID` y no se puede eliminar ni sacar del negocio mientras el negocio exista.
- **RN-BIZ-003 (Alta de sucursal)**: `POST /api/v1/business/branches` con el mismo `CreateStoreCommand` del alta de un local. Nace `PENDING_VERIFICATION` y la aprueba **siempre** el `SUPER_ADMIN` (flujo actual de `stores`). Respeta `max_branches`, que son las sucursales **adicionales** permitidas: la principal no cuenta (con `max_branches = 1` hay la principal y una sucursal; `409` si lo supera; el negocio se bloquea con `SELECT … FOR UPDATE` para que dos altas simultáneas no lo excedan). Hereda la categoría de la principal si no se envía una. En `SHARED_TAX_ID` el NIT es siempre el de la principal (el que envíe el cliente se ignora); en `SEPARATE_TAX_ID` se respeta el enviado. El correo del local sigue siendo único en la plataforma. Se siembra su configuración (recibos, facturas) con sus datos, como en el alta del propio local. Queda en `business_audit` como `BRANCH_CREATED`. Solo el dueño puede usarlo (`403` para cualquier otro).
- **RN-BIZ-004 (Cambio de sucursal)**: `POST /api/v1/auth/switch-store` `{ "storeId": n }` valida que el local sea del negocio del usuario, emite access token con ese `storeId` y fija `refresh_tokens.store_id` de la sesión. Al renovar, `AuthService.refresh` usa `refresh_tokens.store_id` si sigue perteneciendo al negocio; si no, `users.store_id`. Así dos pestañas o dispositivos no se pisan y `users.store_id` sigue siendo la sucursal "de origen". El local destino pasa por el control `StoreNotActive` habitual. Como el JWT firmado no basta (lo pudieron sacar del negocio), `JwtAuthenticationFilter` revalida en cada petición el acceso a la sucursal cuando difiere de `users.store_id` y responde `403 StoreAccessDenied` si lo perdió (ver `_security.md`).
- **RN-BIZ-005 (Facturación)**:
  - `SEPARATE_TAX_ID`: sin cambios en `billing`.
  - `SHARED_TAX_ID`: las facturas y notas de una sucursal salen con la empresa de Alegra, el entorno, el estado de activación y los rangos de numeración de la sucursal principal (`BusinessFacade.billingSourceStoreId`). El documento conserva su propio `store_id`. Esa **fuente se fija en el documento al reservar la emisión** (`billing_store_id`, `V47`; `NULL` = el propio local) y no cambia: aunque después se cambie el modo o se disuelva el negocio, un documento histórico sigue usando la empresa con la que se emitió, y las notas heredan la de su factura. La fuente viaja en `BillingDocumentContext.billingStoreId` y `AlegraAccess.billingStoreId`.
  - **Consecutivo**: `AlegraNumberAllocator.allocate(storeId, billingStoreId, …)` bloquea (`SELECT … FOR UPDATE`) los rangos activos de la sucursal **principal**, así que la principal y todas las sucursales se serializan sobre las mismas filas y nunca repiten número; `UNIQUE (range_id, number)` queda como respaldo. La asignación se guarda a nombre del local del documento (su llave de idempotencia es `(store_id, entorno, documento, referencia)`). Por eso las guardas del rango (en uso, el consecutivo no retrocede) miran **todas** las asignaciones del rango y no solo las del local del rango.
  - **Reintentos**: `InvoiceRetryJob` y `findDueForRetry` exigen facturación `ACTIVE` en la fuente del documento (`COALESCE(billing_store_id, store_id)`).
  - **Cambio de modo**: `PATCH /api/v1/backoffice/businesses/{id}/billing-mode` `{ "billingMode": "…" }` (`SUPER_ADMIN`; auditado como `BILLING_MODE`). Se bloquea el negocio mientras se verifica y responde `409` si alguna sucursal ya tiene facturas `VALIDATED`; mismo modo = sin cambios. Los documentos aún sin fuente fijada toman el modo nuevo; los ya fijados la conservan.
  - **Eliminación**: `DELETE /backoffice/stores/{id}` responde `400` si otras sucursales emitieron documentos con la empresa o la numeración de ese local.
  - Pendiente (revisión independiente, advertencias): en modo compartido una sucursal con su propia configuración de facturación `ACTIVE` todavía podría crear o activar sus propios rangos desde `/api/v1/billing/**`, y el camino de notas sin contexto (`AlegraNoteClient`, `resolve(storeId)`) usaría su propia empresa. Falta un guardia que rechace esa gestión en sucursales con NIT compartido, y la pantalla de facturación de la sucursal que muestre el estado de la principal.
- **RN-BIZ-006 (Eliminación y disolución)**: `DELETE /backoffice/stores/{id}` responde `400` con un mensaje claro si el local es la sucursal principal de un negocio (`StorePurgeDao.findBusinessNameByMainStore`); eliminar otra sucursal funciona igual que hoy (su fila desaparece con su `business_id`). `DELETE /backoffice/users/{id}` responde `400` si el usuario es dueño de un negocio (`AuthService.deleteAccount`). Para liberar a la principal o al dueño, el `SUPER_ADMIN` disuelve el negocio con `DELETE /backoffice/businesses/{id}`: todos los locales quedan independientes (no se elimina ninguno), el negocio se borra y queda `DISSOLVED` en `business_audit`. Las sesiones del dueño pegadas a una sucursal vuelven a su local al renovar. Pendiente para U4: en modo `SHARED_TAX_ID`, las sucursales no tienen empresa de facturación propia al disolverse.
- **RN-BIZ-007 (Planes y usuarios)**: siguen siendo por local (`plans`, tope de usuarios activos). No se comparten entre sucursales.
- **RN-BIZ-008 (Catálogo)**: `INDEPENDENT` significa lo de hoy: cada sucursal tiene sus productos, clientes, proveedores y stock. Por eso las estadísticas consolidadas **no agrupan productos** entre sucursales (no hay identidad común); solo el ranking por sucursal.

## Endpoints

### Back office (`SUPER_ADMIN`, `/api/v1/backoffice/businesses`)
- `POST`: crea negocio `{ name, ownerUserId, mainStoreId, billingMode, catalogMode, maxBranches }`. El dueño debe ser `ADMINISTRATOR` de `mainStoreId`.
- `GET`, `GET /{id}`: lista y detalle (con sucursales).
- `PUT /{id}`: nombre y `maxBranches`.
- `DELETE /{id}`: disuelve el negocio (RN-BIZ-006); `204`.
- `PATCH /{id}/billing-mode`: ver RN-BIZ-005.
- `POST /{id}/stores` `{ storeId }` y `DELETE /{id}/stores/{storeId}`: agrega o saca una sucursal existente (no la principal; el local no puede estar en otro negocio).
- Todo cambio queda en `business_audit` (quién, cuándo, qué).

### Dueño (`ADMINISTRATOR`, `/api/v1/business`)
- `GET /me`: `{ business, activeStoreId }` con el negocio, sus modos y sus sucursales (`storeId`, nombre, estado, si es la principal); `activeStoreId` sale del JWT. El negocio se resuelve por el usuario autenticado. `404` si no es dueño de uno.
- `POST /api/v1/auth/switch-store`: RN-BIZ-004 (vive en `auth`).
- `POST /branches`: RN-BIZ-003; `201` con `{ storeId, name, status, main:false }`.
- `GET /stats/overview?from&to`: `{ from, to, branchCount, totals, byPaymentMethod[], daily[] }` de todas las sucursales juntas. `totals` = `{ salesCount, salesAmount, averageTicket, returnsCount, returnsAmount, netAmount }` (neto = ventas − devoluciones). `byPaymentMethod` va de mayor a menor importe y `daily` por fecha ascendente (los días sin ventas no aparecen).
- `GET /stats/branches?from&to`: `{ from, to, branches[] }` con las mismas cifras por sucursal (`storeId`, `name`, `status`, `main`, `totals`, `lowStockProducts`), ordenadas por venta neta de mayor a menor. `lowStockProducts` son los productos activos con stock igual o menor al mínimo **ahora** (no depende del periodo).
- Ambos exigen `ADMINISTRATOR` dueño de un negocio (`403` si no lo es; `BusinessStatsService`). `from`/`to` son fechas ISO inclusivas; por omisión, los últimos 30 días; `400` si `from` es posterior a `to`, si el periodo supera 366 días o si la fecha es inválida.

Las estadísticas usan SQL nativo con `JdbcClient` (`BusinessStatsDao`, patrón CQRS de `reports`/`BackofficeStatsDao`; excepción deliberada a "cada módulo toca sus tablas", porque cruza `sales`, `sale_returns` y `products` de varios locales). Los locales salen siempre del negocio del usuario autenticado, nunca de la petición, e incluyen todas las sucursales sin importar su estado. El periodo es semiabierto en SQL (`created_at >= desde 00:00` y `< hasta + 1 día`). No se agrupan productos entre sucursales (catálogo independiente, RN-BIZ-008). Mientras la sucursal activa del dueño no esté `ACTIVE`, el filtro de "local no activo" también bloquea estas rutas: debe cambiar a una sucursal que opere.

## Frontend
- **Contexto del negocio** (`context/BusinessContext.jsx`, `hooks/useBusiness`): una sola consulta a `GET /business/me` para el `ADMINISTRATOR` con local; para el resto no hace ninguna llamada y `business` es `null`. Lo leen la cabecera, la barra lateral y la pantalla de revisión del local.
- **Selector de sucursal** (`components/business/BranchSwitcher.jsx`): en la cabecera y en la pantalla de revisión del local; solo aparece si el negocio tiene más de una sucursal. Lista las sucursales con su estado y marca la activa. Al elegir otra llama a `POST /auth/switch-store`, aplica la sesión (`setSessionFromResponse`) y recarga la app desde `/`: todo lo que había en pantalla era de la sucursal anterior. Una sucursal que no opera (en revisión, suspendida…) también se puede elegir, y la pantalla de revisión muestra su estado y el selector para volver.
- **Página `/business`** (`pages/BusinessBento.jsx`, solo `ADMINISTRATOR`; enlace "Mi negocio" en la barra lateral solo si tiene negocio): venta neta de todas las sucursales, serie diaria (con 0 en los días sin ventas), métodos de pago, comparativo y ranking por sucursal, botón "Ir" para cambiar de sucursal y "Nueva sucursal" (`NewBranchModal`; con NIT compartido no pide el NIT; deshabilitado al llegar al tope). El periodo usa el mismo `DateRangeFilter` que el resto de la app (por omisión, 30 días).
- **Back office** (`/backoffice/businesses`, `pages/backoffice/BusinessesBento.jsx`): lista de negocios, `NewBusinessModal` (local principal, dueño resuelto desde el administrador de ese local, modo de facturación, catálogo "Independiente" con "Compartido" deshabilitado y máximo de sucursales) y `BusinessDetailModal` (nombre y máximo, cambio de modo de facturación —el `409` del backend se muestra tal cual—, agregar o sacar sucursales y disolver el negocio con confirmación).
- Servicios `services/businessApi.js` y `services/backoffice/businessesApi.js`; utilidades en `utils/business.js` (etiquetas, `fillDailySeries`).
- **Cambio en `stores` descubierto al integrar**: `GET|PUT /api/v1/stores/me`, sus documentos y `resubmit` ahora trabajan sobre la **sucursal activa** de la sesión (`TenantContext`) cuando el usuario tiene local propio; antes usaban `users.store_id` y la pantalla de revisión habría mostrado el estado del local de origen mientras la API bloqueaba la sucursal. Sin sucursal activa distinta, el comportamiento es el de siempre; un usuario sin local sigue recibiendo "Aún no has registrado un local".

## Plan de implementación (unidades revisables, ~400 líneas c/u)
| # | Unidad | Riesgo |
|---|---|---|
| U1 ✅ | `V45`, módulo `businesses` (entidad, `BusinessFacade`, alta/consulta en backoffice, auditoría) | migración |
| U2 ✅ | Cambio de sucursal + `refresh_tokens.store_id` + `GET /business/me` | **auth / permisos** |
| U3 ✅ | Alta de sucursal por el dueño, `max_branches`, reglas de eliminación | datos |
| U4 ✅ | Modo `SHARED_TAX_ID` en `billing` (resolver + consecutivo por rango) | **concurrencia, contrato de billing** |
| U5 ✅ | Estadísticas consolidadas (DAO + endpoints) | lectura multi-local |
| U6 ✅ | Frontend (selector, `/business`, pantalla back office) | bajo |

U2 y U4 tocan ítems de alto riesgo: cada una va en su propio commit y con verificación independiente.

## Pruebas requeridas
- Aislamiento: el dueño de A no puede cambiar a un local de B; un `SUPERVISOR`/`SELLER` recibe `403` en `/business/**`; las estadísticas de A nunca incluyen datos de B.
- Sesiones: tras `switch`, `refresh` conserva la sucursal; otra sesión del mismo usuario no cambia.
- Facturación compartida: dos sucursales emitiendo en paralelo reciben consecutivos distintos y sin saltos duplicados; `SEPARATE_TAX_ID` no cambia respecto al comportamiento actual (prueba de regresión).
- Cambio de modo bloqueado con facturas `VALIDATED`.
- Migración `V45` en H2 y PostgreSQL (clase `V45MigrationSqlTest` como las existentes).
- Eliminación: la principal se rechaza; el dueño se reapunta.

## Preguntas abiertas
1. ~~¿Una sucursal nueva puede nacer `ACTIVE` sin revisión?~~ Resuelta: siempre pasa por revisión del `SUPER_ADMIN`.
2. ¿Puede haber más de un "admin padre" por negocio? El diseño asume uno (`UNIQUE(owner_user_id)`).
3. En `SHARED_TAX_ID`, ¿alguna sucursal necesitará su propio prefijo de numeración bajo el mismo NIT? Fuera de fase 1: todas usan el rango de la principal.
4. Alegra: confirmar con una prueba en sandbox que varias sucursales pueden emitir contra la misma `company.id` sin conflicto de `reference_code`.
