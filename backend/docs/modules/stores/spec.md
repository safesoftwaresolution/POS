<!-- spec-version: 1.1 | last-updated: 2026-10-02 -->
# Módulo: Stores (Locales / Comercios SaaS)

## Bounded Context
Gestión centralizada de los comercios/locales que utilizan la plataforma POS como clientes SaaS. Permite al equipo de administración (SUPER_ADMIN) monitorear métricas globales, filtrar y buscar locales, gestionar su ciclo de vida y verificar documentos legales/KYC.

## Roles y Permisos
- `SUPER_ADMIN`: Control total (Lectura, Creación, Actualización de estado, Rechazo, Verificación de correo y Revisión de documentos KYC).
- `ADMINISTRATOR`: solo sobre **su propio local** (`/api/v1/stores/**`): registrarlo, consultarlo/editarlo y subir sus documentos KYC.

## Reglas de Negocio
- **RN-STORE-001 (Unicidad de correo)**: Cada local debe tener un correo electrónico único en la plataforma.
- **RN-STORE-002 (Estados válidos)**: Un local transiciona entre `PENDING_VERIFICATION`, `ACTIVE`, `INACTIVE` y `SUSPENDED`.
- **RN-STORE-003 (Documentos KYC)**: Tipos de documento soportados: `RUT`, `COMMERCE_CHAMBER`, `ID_CARD`, `BANK_CERTIFICATE`, `OTHER`. La revisión requiere motivo obligatorio si es rechazado.

- **RN-STORE-004 (Onboarding por autoservicio)**: un dueño se registra con `POST /api/v1/auth/register`, verifica su correo y registra su local con `POST /api/v1/stores`; el local queda en `PENDING_VERIFICATION` hasta que el `SUPER_ADMIN` lo active o rechace (`PATCH .../reject`). Los documentos KYC se almacenan en Cloudflare R2 (módulo `storage`). El frontend lo guía en `/onboarding/store` y muestra la pantalla de revisión pendiente (`PendingStoreReviewBento`) mientras tanto.
- **RN-STORE-005 (Notificaciones)**: `StoreServiceImpl` publica notificaciones vía `NotificationFacade`.

## Endpoints
- `GET /api/v1/backoffice/metrics`: Métricas SaaS consolidadas.
- `GET /api/v1/backoffice/stores`: Búsqueda y listado paginado con filtros por estado y texto.
- `GET /api/v1/backoffice/stores/{id}`: Detalle de local.
- `POST /api/v1/backoffice/stores`: Creación de local.
- `PUT /api/v1/backoffice/stores/{id}`: Actualización de datos.
- `PATCH /api/v1/backoffice/stores/{id}/status`: Cambio de estado.
- `PATCH /api/v1/backoffice/stores/{id}/reject`: Rechazo del local (motivo).
- `PATCH /api/v1/backoffice/stores/{id}/verify-email`: Verificación de correo.
- `GET /api/v1/backoffice/store-status-audit`: Historial de solo lectura de cambios de estado de locales (registro, aprobación, rechazo, suspensión…) con quién y cuándo. Filtros `status` (estado nuevo) y `q` (local, NIT, correo o responsable), paginado. Se alimenta de `store_status_audit` (V18), que solo se inserta; los locales anteriores a V18 tienen una fila de respaldo con `performed_by` nulo.
- `GET /api/v1/backoffice/onboarding/verified-users`: Usuarios `ADMINISTRATOR` que ya verificaron su correo (contraparte de `pending-users`); indica si ya registraron local (`storeId`).
- `DELETE /api/v1/backoffice/stores/{id}`: Eliminación definitiva (solo `SUPER_ADMIN`). Solo locales `REJECTED` que nunca operaron (sin productos, clientes, proveedores, ventas, compras, movimientos, devoluciones ni facturas; si no, `400` indicando qué tablas tienen datos). Borra el local, su dueño y tokens, notificaciones, documentos KYC (y sus archivos en R2, solo tras confirmar la transacción), tickets de soporte, subcategorías, configuración y billing, con SQL nativo en `StorePurgeDao` (excepción deliberada a "cada módulo toca sus tablas": el local es la raíz del tenant). Deja un evento `DELETED` en `store_status_audit`.
- `DELETE /api/v1/backoffice/onboarding/pending-users/{userId}`: Elimina definitivamente a un `ADMINISTRATOR` sin correo verificado y sin local (borra también sus tokens vía `AuthFacade.deleteUnverifiedAccount`). Cualquier otro usuario devuelve `400`.
- `GET /api/v1/backoffice/users`: Directorio de solo lectura de todos los usuarios con su local (nombre y estado) y rol. Filtros `role`, `storeId`, `q` (nombre, usuario, correo o local) y `unassigned=true` (ADMINISTRATOR sin local); paginado. Lectura nativa en `UserDirectoryDao` (módulo `backoffice`).
- `GET /api/v1/backoffice/users/assignable-owners`: ADMINISTRATOR activos con correo verificado y sin local (candidatos a asignar).
- `GET /api/v1/backoffice/stores-without-owner`: Locales no rechazados sin ningún ADMINISTRATOR activo.
- `POST /api/v1/backoffice/stores/{storeId}/assign-owner` (`{ "userId": n }`): Asigna un ADMINISTRATOR verificado y sin local a un local sin administrador activo. Rechaza (`400`) locales rechazados, usuarios que ya tienen local o no son ADMINISTRATOR, usuarios sin verificar y locales que ya tienen administrador: **no mueve usuarios ya asignados**. Queda en `store_user_audit` (V21).
- `GET /api/v1/backoffice/user-assignments`: Historial de solo lectura de esas asignaciones (más recientes primero).
- `GET /api/v1/backoffice/stores/{id}/documents`: Listado de documentos KYC.
- `PATCH /api/v1/backoffice/stores/{id}/documents/{docId}`: Aprobación/Rechazo de documento.
- `GET /api/v1/backoffice/store-categories`: Listado de categorías de comercios.
- `POST /api/v1/backoffice/store-categories`: Creación de categoría.
- `PUT /api/v1/backoffice/store-categories/{id}`: Edición de categoría.
- `DELETE /api/v1/backoffice/store-categories/{id}`: Desactivación de categoría.

### Autoservicio del dueño (`ADMINISTRATOR`)
- `POST /api/v1/stores`: Registrar mi local (el administrador aún no tiene uno).
- `GET /api/v1/stores/me` / `PUT /api/v1/stores/me`: Consultar / editar mi local.
- `GET /api/v1/stores/me/documents`: Listar mis documentos KYC.
- `POST /api/v1/stores/me/documents`: Subir un documento KYC (multipart).

### Back office relacionado (módulo `backoffice`, solo `SUPER_ADMIN`)
- `GET /api/v1/backoffice/onboarding/pending-users` y `GET /api/v1/backoffice/onboarding/stats`: cola y estadísticas de onboarding.
- `/api/v1/backoffice/staff` (CRUD, `PATCH /{id}/status`, `PATCH /{id}/password`): gestión del personal de plataforma (usuarios `SUPER_ADMIN`).
