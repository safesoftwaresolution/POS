# Módulo: Support (PQRs y Reportes de Bugs)

## Bounded Context
Recepción, radicación, seguimiento y resolución de Peticiones, Quejas, Reclamos, Sugerencias (PQRs) y Reportes de Bugs del Sistema.

## Roles y Permisos
- **Público / Autenticado**: Radicación de tickets (`POST /api/v1/support/tickets`) y consulta por radicado (`GET /api/v1/support/tickets/track/{ticketNumber}`).
- `SUPER_ADMIN`: Gestión, filtros avanzados, métricas y resolución (`/api/v1/backoffice/support/**`).

## Reglas de Negocio
- **RN-SUPP-001 (Tipos)**: `PETITION`, `COMPLAINT`, `CLAIM`, `SUGGESTION`, `BUG_REPORT`.
- **RN-SUPP-002 (Prioridades)**: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
- **RN-SUPP-003 (Estados)**: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`.
- **RN-SUPP-004 (Número de Radicado)**: formato `PQR-YYYYMMDD-XXXXXX` o `BUG-YYYYMMDD-XXXXXX`, con 6 caracteres aleatorios (alfabeto sin `0/O/1/I`, `SecureRandom`), único en BD. **No es secuencial**: el seguimiento es público y se consulta solo por este número, y un contador secuencial (que además vivía en memoria y colisionaba tras reiniciar) permitía enumerar tickets ajenos.
- **RN-SUPP-005 (Seguimiento público mínimo)**: `GET …/track/{ticketNumber}` devuelve `TicketTrackingDto` (radicado, tipo, prioridad, estado, título, notas de resolución y fechas). **No** devuelve correo, teléfono, descripción ni `systemInfo`; esos datos solo los ve `SUPER_ADMIN` en el back office.
- **RN-SUPP-006 (Radicación pública acotada)**: el `POST` valida con `@Valid`: `type`, `title` y `description` obligatorios, correo con formato válido y longitudes máximas (título 200, descripción 4000, `systemInfo` 2000). Pendiente: rate limiting y no aceptar `storeId` del cliente sin verificarlo.

## Endpoints
- `POST /api/v1/support/tickets`: Radicar ticket (público).
- `GET /api/v1/support/tickets/track/{ticketNumber}`: Tracking por radicado (público).
- `GET /api/v1/backoffice/support/tickets`: Listado paginado con filtros.
- `GET /api/v1/backoffice/support/tickets/{id}`: Detalle del ticket.
- `PATCH /api/v1/backoffice/support/tickets/{id}`: Actualizar estado, prioridad y resolución.
- `GET /api/v1/backoffice/support/metrics`: Métricas de soporte.