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
