<!-- spec-version: 1.0 | last-updated: 2026-10-02 -->
# Módulo: Notifications (Notificaciones por Local)

## Bounded Context
Persistencia y consulta de notificaciones internas dirigidas a un local (alertas en el dashboard). Otros módulos publican notificaciones a través de `NotificationFacade.createNotification(...)`; este módulo no decide cuándo se genera una alerta.

## Dependencias
- **Requiere**: ninguna (usa `TenantContext` para el local en sesión).
- **Requerido por**: `inventory` (alerta de stock bajo, `LowStockAlertNotifier`), `billing` (cambios de habilitación, `StoreBillingConfigServiceImpl`), `stores` (eventos de onboarding, `StoreServiceImpl`).

## Roles y Permisos
- `ADMINISTRATOR`, `SUPERVISOR`, `SELLER`: leer y marcar como leídas las notificaciones **de su propio local**.
- `SUPER_ADMIN`: sin acceso (no pertenece a ningún local).

## Reglas de Negocio
- **RN-NOTIF-001 (Aislamiento)**: toda lectura/modificación se filtra por el `storeId` de `TenantContext`; una notificación de otro local no es accesible.
- **RN-NOTIF-002 (Orden)**: el listado devuelve las más recientes primero.
- **RN-NOTIF-003 (Creación solo interna)**: no existe endpoint para crear notificaciones; solo otros módulos vía `NotificationFacade`.

## Modelo de Datos
Tabla `notifications` (introducida en `V11__Notifications.sql`). `NotificationDto`: `id`, `type`, `title`, `message`, `linkPath` (ruta del frontend a la que lleva la alerta), `read`, `createdAt`.

## Endpoints
- `GET /api/v1/notifications` — notificaciones del local en sesión.
- `GET /api/v1/notifications/unread-count` — `{ "count": n }`.
- `PATCH /api/v1/notifications/{id}/read` — marcar una como leída.
- `PATCH /api/v1/notifications/read-all` — marcar todas como leídas.
