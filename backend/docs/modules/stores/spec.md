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
- **RN-STORE-005 (Onboarding)**: `POST /api/v1/stores` crea el local del administrador autenticado y se lo asigna (`users.store_id`). El token emitido antes de ese paso no lleva `storeId`; el frontend debe volver a iniciar sesión o refrescar.

## Endpoints

### Back office (SUPER_ADMIN)
- `GET /api/v1/backoffice/metrics`: métricas SaaS consolidadas (nota: el `total` no suma los `REJECTED`).
- `GET /api/v1/backoffice/stores`: búsqueda y listado paginado con filtros por estado y texto.
- `GET /api/v1/backoffice/stores/{id}`: detalle de local.
- `POST /api/v1/backoffice/stores`: creación de local.
- `PUT /api/v1/backoffice/stores/{id}`: actualización de datos.
- `PATCH /api/v1/backoffice/stores/{id}/status`: cambio de estado.
- `PATCH /api/v1/backoffice/stores/{id}/reject`: rechazo con motivo (`{ "reason": "..." }`).
- `PATCH /api/v1/backoffice/stores/{id}/verify-email`: verificación de correo.
- `GET /api/v1/backoffice/stores/{id}/documents`: listado de documentos KYC.
- `PATCH /api/v1/backoffice/stores/{id}/documents/{docId}`: aprobación/rechazo de documento.
- `GET|POST /api/v1/backoffice/store-categories`, `PUT|DELETE /api/v1/backoffice/store-categories/{id}`: categorías de comercios (DELETE desactiva).

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
