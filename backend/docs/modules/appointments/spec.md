<!-- spec-version: 1.2 | last-updated: 2026-11-05 -->
# Módulo: Citas (Appointments)

Calendario y agendamiento de citas para los locales que tienen la plantilla `APPOINTMENTS` (barbería, peluquería, salón de uñas y pies, boutique, o cualquier local al que el `SUPER_ADMIN` se la sume como extra; ver RN-STORE-006 en el módulo stores). Versión mínima: agendar, ver por día o semana, mover y cancelar.

## Dependencias
- **Requiere**: stores (`StoreFacade.getTemplatesForStore`, para exigir la plantilla), customers (`CustomerFacade.getById`), services (`ServiceFacade.getById`, de donde sale la duración).
- **Requerido por**: nadie por ahora.

## Reglas de Negocio

### RN-APPT-001 — Solo plantilla de citas
- Todas las operaciones exigen un local en el contexto y que entre sus plantillas esté `APPOINTMENTS` (la de su categoría o un extra del local; ver RN-STORE-006); si no, `403`. El backend decide: ocultar el menú en el frontend es solo comodidad.

### RN-APPT-002 — Duración
- El fin de la cita es el inicio más `services.duration_minutes`; si el servicio no tiene duración, 30 minutos. Mover una cita conserva su duración.

### RN-APPT-003 — Datos obligatorios
- Cliente, servicio e inicio son obligatorios. El cliente y el servicio deben ser del local actual; un servicio inactivo no se puede agendar. Las notas admiten hasta 255 caracteres.

### RN-APPT-004 — Estados
- `SCHEDULED` y `CANCELLED`. Una cita cancelada no se puede mover; cancelar una ya cancelada no cambia nada.

### RN-APPT-005 — Copia de nombres
- Se guardan `customer_name` y `service_name` al agendar, para que editar o desactivar el cliente o el servicio no reescriba la agenda.

### RN-APPT-006 — Sin solapes en la agenda del local
- El local tiene una sola agenda (aún no hay profesionales): no puede haber dos citas `SCHEDULED` que se crucen. Una cita de 9:00 a 10:00 bloquea cualquier otra que empiece antes de las 10:00 y termine después de las 9:00; los extremos pueden tocarse (10:00 a 10:30 es válido).
- Se valida al agendar y al mover (la cita que se mueve no choca consigo misma). Las citas canceladas no cuentan. Si hay choque responde `409` con "Ya hay una cita agendada en ese horario".
- Siguen pendientes los profesionales con agenda propia (ver `_roadmap.md`): con ellos el cruce se evaluará por profesional.

### RN-APPT-007 — Consulta por rango
- `GET` exige `from` y `to`; se devuelven las citas cuyo inicio cae en `[from, to)`, ordenadas por inicio. Rango máximo de 62 días (`400` si es mayor o inválido).

## API Endpoints
- `GET /api/v1/appointments?from=&to=`: citas del rango (`from`/`to` en ISO `YYYY-MM-DDTHH:mm:ss`).
- `POST /api/v1/appointments` (`{ customerId, serviceId, startAt, notes }`): agenda; responde `201` con la cita.
- `PATCH /api/v1/appointments/{id}/reschedule` (`{ startAt }`): mueve la cita.
- `PATCH /api/v1/appointments/{id}/cancel`: cancela la cita.

## Modelo de Datos
Tabla `appointments` (`V34`): `store_id`, `customer_id`, `customer_name`, `service_id`, `service_name`, `start_at`, `end_at` (`CK_appointments_range`: `end_at > start_at`), `status`, `notes`, `created_at`, `updated_at`. Índice `IDX_appointments_store_start (store_id, start_at)`. Claves foráneas a `stores`, `customers` y `services`. El estado no lleva `CHECK`: lo valida el enum `AppointmentStatus` (H2 2.4.x falla con `CHECK … IN (…)`).

## Permisos
- `ADMINISTRATOR`, `SUPERVISOR` y `SELLER` pueden consultar, agendar, mover y cancelar (la agenda la opera quien atiende en recepción). `SUPER_ADMIN` no tiene local y no usa este módulo.

## Frontend
Página `/appointments` (`AppointmentsBento`), con vista Día/Semana, solo visible y accesible si `useStoreTemplate().features.appointments`. La guarda `RequireFeature` no redirige mientras carga la plantilla.
