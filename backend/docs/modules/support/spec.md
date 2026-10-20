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
- **RN-SUPP-004 (Número de Radicado)**: Formato automático secuencial por fecha: `PQR-YYYYMMDD-NNNN` o `BUG-YYYYMMDD-NNNN`.

## Endpoints
- `POST /api/v1/support/tickets`: Radicar ticket (público).
- `GET /api/v1/support/tickets/track/{ticketNumber}`: Tracking por radicado (público).
- `GET /api/v1/backoffice/support/tickets`: Listado paginado con filtros.
- `GET /api/v1/backoffice/support/tickets/{id}`: Detalle del ticket.
- `PATCH /api/v1/backoffice/support/tickets/{id}`: Actualizar estado, prioridad y resolución.
- `GET /api/v1/backoffice/support/metrics`: Métricas de soporte.