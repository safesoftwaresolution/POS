<!-- spec-version: 1.0 | last-updated: 2026-10-16 -->
# Módulo: Notifications (Notificaciones en la app)

## Dependencias
- **Requiere**: ninguno.
- **Requerido por** (publican con `NotificationFacade.createNotification`): `inventory` (`LOW_STOCK`), `stores` (`DOCUMENT_APPROVED` / `DOCUMENT_REJECTED`), `billing` (`BILLING_STATUS_CHANGED`).

## Bounded Context
Bandeja de avisos por local, visible en la aplicación. Es independiente del correo (`shared/email`), que algunos productores envían además.

## Modelo de Datos — `notifications` (`V11__Notifications.sql`)
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGSERIAL | PK |
| store_id | BIGINT | NOT NULL, FK → stores(id) |
| type | VARCHAR(40) | NOT NULL (`LOW_STOCK`, `DOCUMENT_APPROVED`, `DOCUMENT_REJECTED`, `BILLING_STATUS_CHANGED`) |
| title | VARCHAR(150) | NOT NULL |
| message | VARCHAR(500) | NOT NULL |
| link_path | VARCHAR(255) | NULLABLE (ruta del frontend a la que lleva el aviso) |
| read | BOOLEAN | NOT NULL, DEFAULT FALSE |
| created_at | TIMESTAMP | NOT NULL |

Índice: `IDX_notifications_store`.

## Reglas de Negocio
- **RN-NOT-001 (Alcance por local)**: las lecturas y el marcado usan el `storeId` de `TenantContext`; una notificación de otro local se trata como inexistente.
- **RN-NOT-002 (Publicación explícita)**: `createNotification` recibe el `storeId` en el comando (no lo toma del contexto), porque quien publica suele ser un proceso `@Async` o el back office.
- **RN-NOT-003 (Alerta de stock bajo)**: `inventory` publica `LOW_STOCK` solo al **cruzar** el stock mínimo hacia abajo, no en cada movimiento posterior.

## Roles y Permisos
`ADMINISTRATOR`, `SUPERVISOR` y `SELLER` acceden a todos los endpoints. `SUPER_ADMIN` no (no pertenece a ningún local).

## API Endpoints (`/api/v1/notifications`)
- `GET /api/v1/notifications` — lista del local, más recientes primero (sin paginar).
- `GET /api/v1/notifications/unread-count` — cantidad sin leer.
- `PATCH /api/v1/notifications/{id}/read` — marca una como leída.
- `PATCH /api/v1/notifications/read-all` — marca todas como leídas.

## Pendiente
- El listado no está paginado.
- `LowStockAlertNotifier` se dispara dentro de la transacción del movimiento: si ésta revierte, el aviso ya salió (usar `AFTER_COMMIT`).
