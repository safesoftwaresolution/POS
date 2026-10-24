<!-- spec-version: 1.1 | last-updated: 2026-10-16 -->
# Módulo: Stores (Locales / Comercios SaaS)

## Dependencias
- **Requiere**: users (asignar el local al administrador), storage (documentos KYC), notifications (aviso al aprobar/rechazar documentos)
- **Requerido por**: billing (el local es la unidad de habilitación), todos los módulos con `store_id`

## Bounded Context
Ciclo de vida de los comercios/locales de la plataforma SaaS: el **auto-registro** del dueño (onboarding), la verificación de documentos legales/KYC y la gestión desde el back office.

## Roles y Permisos
- `SUPER_ADMIN`: control total bajo `/api/v1/backoffice/**` (métricas, listado, alta, edición, estado, rechazo, verificación de correo y revisión de documentos KYC, categorías de comercio).
- `ADMINISTRATOR` **sin local** (recién registrado) o con local: `/api/v1/stores/**` para registrar y consultar **su propio** local y subir sus documentos.

## Reglas de Negocio
- **RN-STORE-001 (Unicidad de correo)**: cada local tiene un correo único en la plataforma.
- **RN-STORE-002 (Estados válidos)**: `PENDING_VERIFICATION`, `ACTIVE`, `INACTIVE`, `SUSPENDED` y `REJECTED` (este último desde `V7`; guarda `rejection_reason`). Los estados son `String` sin enum ni validación de transiciones.
- **RN-STORE-003 (Documentos KYC)**: tipos `RUT`, `COMMERCE_CHAMBER`, `ID_CARD`, `BANK_CERTIFICATE`, `OTHER`; estados de documento `PENDING`, `APPROVED`, `REJECTED`. La revisión exige motivo si se rechaza. Al revisar se notifica al local (`DOCUMENT_APPROVED` / `DOCUMENT_REJECTED`).
- **RN-STORE-004 (Rechazo)**: `PATCH …/reject` exige motivo y deja el local en `REJECTED`. `PATCH …/status` acepta `REJECTED` sin motivo (inconsistencia conocida).
- **RN-STORE-005 (Onboarding)**: `POST /api/v1/stores` crea el local del administrador autenticado y se lo asigna (`users.store_id`). El token emitido antes de ese paso no lleva `storeId`; el frontend debe volver a iniciar sesión o refrescar. El frontend guía el alta en `/onboarding/store` y muestra la pantalla de revisión pendiente (`PendingStoreReviewBento`) mientras el `SUPER_ADMIN` decide.

## Endpoints

### Back office (SUPER_ADMIN)
- `GET /api/v1/backoffice/metrics`: métricas SaaS consolidadas (nota: el `total` no suma los `REJECTED`).
- `GET /api/v1/backoffice/stores`: búsqueda y listado paginado con filtros por estado y texto.
- `GET /api/v1/backoffice/stores/{id}`: detalle de local.
- `POST /api/v1/backoffice/stores`: creación de local.
- `PUT /api/v1/backoffice/stores/{id}`: actualización de datos.
- `PATCH /api/v1/backoffice/stores/{id}/status`: cambio de estado. Al pasar de `PENDING_VERIFICATION` a `ACTIVE` (aprobación) se envía, tras confirmar la transacción y en segundo plano (`StoreApprovalMailer`, `@Async`), un correo con la marca BentoPOS a los administradores del local (o al correo del local si aún no tiene) avisando que fue aprobado, con un botón a `/login`. Reactivar un local suspendido no lo envía; un fallo de SMTP no revierte la aprobación. La pantalla "en revisión" del administrador consulta el estado cada 15 s y abre el POS sola al aprobarse.
- `PATCH /api/v1/backoffice/stores/{id}/reject`: rechazo con motivo (`{ "reason": "..." }`).
- `PATCH /api/v1/backoffice/stores/{id}/verify-email`: verificación de correo.
- `GET /api/v1/backoffice/stores/{id}/documents`: listado de documentos KYC.
- `PATCH /api/v1/backoffice/stores/{id}/documents/{docId}`: aprobación/rechazo de documento.
- `GET|POST /api/v1/backoffice/store-categories`, `PUT|DELETE /api/v1/backoffice/store-categories/{id}`: categorías de comercios (DELETE desactiva).
- `GET /api/v1/backoffice/store-status-audit`: Historial de solo lectura de cambios de estado de locales (registro, aprobación, rechazo, suspensión…) con quién y cuándo. Filtros `status` (estado nuevo) y `q` (local, NIT, correo o responsable), paginado. Se alimenta de `store_status_audit` (V18), que solo se inserta; los locales anteriores a V18 tienen una fila de respaldo con `performed_by` nulo.
- `GET /api/v1/backoffice/onboarding/verified-users`: Usuarios `ADMINISTRATOR` que ya verificaron su correo (contraparte de `pending-users`); indica si ya registraron local (`storeId`).
- `DELETE /api/v1/backoffice/stores/{id}` (body opcional `{ "confirmName": "..." }`): Eliminación definitiva de un local de **cualquier estado** con todos sus datos (solo `SUPER_ADMIN`). Borra productos, subcategorías, clientes, proveedores, ventas, compras, movimientos, devoluciones, facturas, notificaciones, documentos KYC (y sus archivos en R2, solo tras confirmar la transacción), tickets de soporte, configuración y billing, y al personal (`SUPERVISOR`, `SELLER`), con SQL nativo en `StorePurgeDao` (excepción deliberada a "cada módulo toca sus tablas": el local es la raíz del tenant). **Los `ADMINISTRATOR` del local no se eliminan**: quedan con `store_id = NULL` (activos y verificados), se les cierran las sesiones y reaparecen como candidatos a asignar; el `SUPER_ADMIN` puede reasignarlos o eliminarlos desde el directorio de usuarios. Salvaguardas: `400` si el local tiene facturas electrónicas `VALIDATED` (documentos fiscales; se debe suspender o inactivar), y `400` si falta `confirmName` con el nombre exacto del local (sin distinguir mayúsculas), que solo se omite para un local `REJECTED` que nunca operó. Deja un evento `DELETED` en `store_status_audit` con el snapshot del local y los datos de operación que tenía.
- `DELETE /api/v1/backoffice/onboarding/pending-users/{userId}`: Elimina definitivamente a un `ADMINISTRATOR` sin correo verificado y sin local (borra también sus tokens vía `AuthFacade.deleteUnverifiedAccount`). Cualquier otro usuario devuelve `400`.
- `GET /api/v1/backoffice/users`: Directorio de solo lectura de todos los usuarios con su local (nombre y estado) y rol. Filtros `role`, `storeId`, `q` (nombre, usuario, correo o local) y `unassigned=true` (ADMINISTRATOR sin local); paginado. Lectura nativa en `UserDirectoryDao` (módulo `backoffice`).
- `PATCH /api/v1/backoffice/users/{userId}/status` (`{"active": bool}`): Inactiva o reactiva a un usuario (vía `AuthFacade.setAccountActive`). Al inactivar revoca sus sesiones. `400` si es `SUPER_ADMIN`, uno mismo o el último administrador activo del local; reactivar respeta el tope del plan.
- `DELETE /api/v1/backoffice/users/{userId}`: Elimina definitivamente a un usuario (vía `AuthFacade.deleteAccount`, borra también sus tokens). `400` si es `SUPER_ADMIN`, uno mismo o el único administrador activo del local; `409` si tiene ventas, compras, movimientos o devoluciones (solo se puede inactivar). No deja registro en las auditorías: queda solo en el log.
- `GET /api/v1/backoffice/users/assignable-owners`: ADMINISTRATOR activos con correo verificado y sin local (candidatos a asignar).
- `GET /api/v1/backoffice/stores-without-owner`: Locales no rechazados sin ningún ADMINISTRATOR activo.
- `POST /api/v1/backoffice/stores/{storeId}/assign-owner` (`{ "userId": n }`): Asigna un ADMINISTRATOR verificado y sin local a un local sin administrador activo. Rechaza (`400`) locales rechazados, usuarios que ya tienen local o no son ADMINISTRATOR, usuarios sin verificar y locales que ya tienen administrador: **no mueve usuarios ya asignados**. Queda en `store_user_audit` (V21).
- `GET /api/v1/backoffice/user-assignments`: Historial de solo lectura de esas asignaciones (más recientes primero).
- `GET /api/v1/backoffice/plans`: Planes (`code`, `name`, `maxAdditionalUsers`, `maxActiveUsers`) del más básico al más alto. Sembrados en `V22`: `BASIC` (1 adicional), `STANDARD` (4), `PRO` (10); los límites se ajustan en la tabla `plans` sin desplegar.
- `PATCH /api/v1/backoffice/stores/{id}/plan` (`{ "planCode": "PRO" }`): Cambia el plan de un local. Los locales existentes pasaron a `PRO` en `V22` y los nuevos empiezan en `BASIC`. Bajar de plan no desactiva usuarios (ver RN-USER-005 en el módulo users). Queda en `store_status_audit` como evento `PLAN_CHANGED` con el motivo `Plan: X -> Y`.
- `GET /api/v1/backoffice/store-plan-usage`: Plan de cada local y usuarios activos que usa frente a su tope (lectura nativa en `UserDirectoryDao`).
- `GET /api/v1/backoffice/stats/overview`: Panorama de toda la plataforma para el `SUPER_ADMIN` (solo lectura, módulo `backoffice`, agregaciones nativas en `BackofficeStatsDao`): indicadores (locales totales y activos, usuarios activos, locales nuevos, ventas e importe de los últimos 30 días, locales con ventas, locales en el tope de usuarios de su plan), locales por estado y por plan, usuarios activos por rol, facturas electrónicas por estado, locales nuevos y ventas (cantidad e importe) por mes en los últimos 12 meses (los meses sin datos vienen en 0) y el top 5 de locales por importe vendido en 30 días. La UI lo dibuja con SVG/CSS, sin librería de gráficos.

### Auto-servicio del administrador (`/api/v1/stores`, rol `ADMINISTRATOR`)
- `POST /api/v1/stores`: registrar el propio local (`CreateStoreCommand`).
- `GET /api/v1/stores/me`: consultar el propio local. `PUT /api/v1/stores/me`: editarlo (`UpdateOwnStoreCommand`).
- `GET /api/v1/stores/me/documents`: documentos propios. `POST /api/v1/stores/me/documents` (multipart): subir un documento.

## Modelo de Datos
Tablas `store_categories`, `stores` y `store_documents` (`V3`; `V7` añade `stores.rejection_reason`). Ver `_database-schema.md`.

## Pendiente
- Faltan enum de estados, validación de transiciones y un formato de spec completo (modelo de datos y RF numerados).
- La subida de documentos solo comprueba que el archivo no esté vacío (el tipo/tamaño se limita en `storage` solo para su endpoint HTTP) y deja la URL pública.
- `StoreFacade` expone `MultipartFile` (tipo web) en la API pública del módulo.
- Sin tests de controlador ni de roles.
