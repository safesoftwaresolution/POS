<!-- spec-version: 1.0 | last-updated: 2026-10-16 -->
# Módulo: Backoffice (Operadores de plataforma y embudo de registro)

## Dependencias
- **Requiere**: `users` (`UserFacade`). `OnboardingStatsDao` consulta además las tablas `users` y `stores` directamente con SQL de solo lectura (excepción CQRS declarada, igual que `reports`).
- **Requerido por**: nadie.

## Bounded Context
Administración de las cuentas de operador de la plataforma (`SUPER_ADMIN`) y métricas del embudo de auto-registro. El resto del back office vive en sus módulos: `stores`, `legal`, `support`, `categories`, `billing` y `settings/parameters` exponen sus rutas bajo `/api/v1/backoffice/**`.

## Roles y Permisos
Todo el módulo es **solo `SUPER_ADMIN`** (`@PreAuthorize` a nivel de clase y regla `/api/v1/backoffice/**` en `SecurityConfig`).

## Reglas de Negocio
- **RN-BO-001 (Staff = SUPER_ADMIN sin local)**: las cuentas gestionadas aquí son siempre `SUPER_ADMIN` con `store_id = null`.
- **RN-BO-002 (Sin auto-bloqueo)**: no se puede desactivar ni eliminar la propia cuenta (reglas heredadas de `users`).
- **RN-BO-003 (Embudo)**: `pendingUsers` = administradores con correo sin verificar; `pendingStores` = locales pendientes de revisión; `approvedStoresLast30Days` y `rejectedStoresTotal` miden el resultado de la revisión.

## API Endpoints
**Staff** (`/api/v1/backoffice/staff`)
- `GET /` — listado paginado. `GET /{id}` — detalle.
- `POST /` — crear (`CreateBackofficeStaffCommand`). `PUT /{id}` — actualizar.
- `PATCH /{id}/status` — body `{ "active": true|false }`. `PATCH /{id}/password` — restablecer contraseña.
- `DELETE /{id}` — desactivar (borrado lógico).

**Onboarding** (`/api/v1/backoffice/onboarding`)
- `GET /pending-users` — administradores pendientes de verificar el correo (paginado).
- `GET /stats` — `OnboardingStatsDto { pendingUsers, pendingStores, approvedStoresLast30Days, rejectedStoresTotal }`.

## Modelo de Datos
Sin tablas propias (opera sobre `users` y `stores`).

## Pendiente
- `BackofficeStaffServiceImpl` mapea a DTO dentro del servicio; falta un mapper en `internal/mappers/`.
- No hay tests de controlador ni de servicio.
- Nada impide desactivar o borrar al último `SUPER_ADMIN` (RN-USER-002 solo protege a `ADMINISTRATOR`).
