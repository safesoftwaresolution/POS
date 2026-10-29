<!-- spec-version: 1.0 | last-updated: 2026-10-29 -->
# Módulo: Auditoría (Audit)

Bitácora genérica de cambios: quién hizo qué y cuándo sobre una entidad del local. Hoy la usa `customers` (alta, edición y baja); está pensada para que otros módulos (productos, servicios, ventas…) la usen sin cambiar su estructura.

## Dependencias
- **Requiere**: ninguna. El módulo no depende de `users`: quien lo llama le pasa el id y el nombre de usuario.
- **Requerido por**: customers (registra los cambios de clientes).

## Reglas de Negocio

### RN-AUD-001 — Una Fila por Cambio
- `AuditFacade.record(entityType, entityId, action, userId, username, changes)` guarda una fila en `audit_log`. `action` es `CREATE`, `UPDATE` o `DELETE`. Corre en la transacción de quien llama: si el cambio se revierte, el registro también.
- `changes` son los campos modificados con su valor anterior y nuevo: `{"fullName": {"from": "Maria", "to": "Maria Lopez"}}`. En un alta, `from` es null. Puede ir vacío (por ejemplo en una baja).
- Quien audita solo registra cambios reales: guardar sin modificar nada no genera un registro.

### RN-AUD-002 — Inmutable y con Copia del Usuario
- Los registros no se editan ni se borran. Guardan el id **y el nombre de usuario** de quien actuó, sin llave foránea a `users`, para que el historial se conserve aunque el usuario cambie o se desactive.

### RN-AUD-003 — Aislamiento por Tienda
- Todo registro lleva el `store_id` del contexto (`TenantContext`); sin local activo no se registra ni se consulta. Un local nunca ve la bitácora de otro.

### RN-AUD-004 — Quién Puede Consultarla
- Solo el `ADMINISTRATOR` del local consulta el historial. El resto de roles ve únicamente "creado por" y "editado por" en la propia pantalla del cliente.

## API Endpoints

### GET /api/v1/audit
- **Permisos**: Admin
- **Query params**: `entityType` (ej. `CUSTOMER`, obligatorio), `entityId` (obligatorio), `page`, `size` (50 por defecto)
- **Response 200**: página de `AuditEntryDto` (`id`, `entityType`, `entityId`, `action`, `userId`, `username`, `changes`, `createdAt`), del más reciente al más antiguo.

## Modelo de Datos

### Tabla: `audit_log` (`V30`)
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| store_id | BIGINT | FK → stores(id), NOT NULL |
| entity_type | VARCHAR(40) | NOT NULL |
| entity_id | BIGINT | NOT NULL |
| action | VARCHAR(20) | NOT NULL (`CREATE`, `UPDATE`, `DELETE`) |
| user_id | BIGINT | NULLABLE (sin FK) |
| username | VARCHAR(50) | NULLABLE |
| changes | TEXT | NULLABLE (JSON) |
| created_at | TIMESTAMP | NOT NULL |

**Índice**: `IDX_audit_log_entity` (`store_id`, `entity_type`, `entity_id`, `created_at`).

## Permisos
| Operación | Admin | Supervisor | Vendedor |
|---|---|---|---|
| Consultar el historial | ✓ | - | - |

## Fuera de alcance
- Auditar productos, servicios, ventas y usuarios (el módulo ya lo permite; falta que cada uno llame a `record`).
- Consulta global de la bitácora (hoy solo por entidad) y exportación.
- Purga o retención de registros antiguos.
