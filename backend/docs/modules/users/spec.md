<!-- spec-version: 1.1 | last-updated: 2026-10-02 -->
# Módulo: Usuarios (Users)

## Dependencias
- **Requiere**: ninguno
- **Requerido por**: auth, sales, purchases, inventory

## Reglas de Negocio

### RN-USER-001 — Roles Estáticos y Fijos
- Solo existen: `SUPER_ADMIN` (plataforma, sin `store_id`, gestionado desde `/api/v1/backoffice/staff`), `ADMINISTRATOR`, `SUPERVISOR`, `SELLER` (estos tres con `store_id`). No hay CRUD de roles.

### RN-USER-003 — Tope de usuarios por plan
Cada local tiene un plan (módulo `stores`, tabla `plans`) que limita sus usuarios **activos**: el administrador cuenta como uno y el plan añade `max_additional_users` más (Básico 1, Estándar 4, Pro 10 → 2, 5 y 11 usuarios activos). El tope se aplica al **crear** un usuario y al **reactivarlo**; desactivar o eliminar (borrado lógico) libera el cupo. Bajar de plan no desactiva a nadie: el local conserva sus usuarios pero no puede crear ni reactivar más hasta quedar dentro del tope. El plan limita solo la cantidad, no el rol. `SUPER_ADMIN` no cuenta ni está limitado.

`users` no depende de `stores`: define el puerto `UserQuotaPolicy` (con `UserQuota`) y `stores` lo implementa (`PlanUserQuotaPolicy`), evitando un ciclo entre módulos.

### RN-USER-002 — Autoconservación del Administrador
- No se puede eliminar ni desactivar al último usuario con rol Administrador.

## Requerimientos Funcionales

### RF-USER-001 — Crear Usuario
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Campos: fullName, username (único), email (único), password (temporal), role.
  - Estado inicial: Activo.
  - Contraseña: mínimo 8 caracteres, al menos 1 letra y 1 número.
  - Validar unicidad de username y email **sin distinguir mayúsculas** (`AndresV` y `andresv` son el mismo usuario). El login normaliza el usuario a minúsculas, por eso las búsquedas por username y email del repositorio también lo ignoran; antes eran exactas y un usuario registrado con mayúsculas recibía `401` con la contraseña correcta.

### RF-USER-002 — Editar Usuario
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Editable: fullName, email, role.
  - No editable: username.
  - Validar email no duplicado.

### RF-USER-003 — Activar/Desactivar Usuario
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Admin puede desactivar usuario → revoca acceso inmediato.
  - Admin NO puede desactivarse a sí mismo.
  - Sesiones activas se invalidan en siguiente petición.

### RF-USER-004 — Eliminar Usuario (Borrado Lógico)
- **Prioridad**: Media
- **Criterios de Aceptación**:
  - Borrado lógico si el usuario tiene transacciones históricas.
  - No aparece en listados activos.

### RF-USER-005 — Restablecer Contraseña
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Admin/Supervisor puede generar contraseña temporal para otro usuario.
  - El propio usuario puede cambiar su contraseña proporcionando la actual.

## Requerimientos No Funcionales

### RNF-USER-001 — Encriptación BCrypt
- Contraseñas hasheadas con BCrypt, factor de costo ≥ 10.

## API Endpoints

### POST /api/v1/users
- **Permisos**: Administrador
- **Request**:
  ```json
  { "fullName": "Juan Perez", "username": "juanp", "email": "juan@tienda.com", "password": "TemporalPassword1", "role": "SELLER" }
  ```
- **Response 201**:
  ```json
  { "id": 5, "fullName": "Juan Perez", "username": "juanp", "email": "juan@tienda.com", "role": "SELLER", "active": true }
  ```
- **Errores**: 400 (username/email duplicado, contraseña débil)

### GET /api/v1/users
- **Permisos**: Administrador
- **Response 200**: Lista paginada de usuarios

### PUT /api/v1/users/{id}
- **Permisos**: Administrador
- **Errores**: 400, 404

### PATCH /api/v1/users/{id}/status
- **Permisos**: Administrador
- Activa/desactiva usuario

### DELETE /api/v1/users/{id}
- **Permisos**: Administrador
- Borrado lógico

### GET /api/v1/users/quota
- **Permisos**: ADMINISTRATOR
- **Response 200**: `{ "planName": "Básico", "activeUsers": 2, "maxActiveUsers": 2, "canCreateMore": false }` del local del llamador. `204` si el local no tiene tope aplicable.
- La UI lo usa para mostrar el uso del plan y deshabilitar «Nuevo usuario» al llegar al tope. Crear o reactivar por encima del tope devuelve `400` con el motivo.

### PATCH /api/v1/users/{id}/password
- **Permisos**: Administrador, Supervisor (para otros) / Usuario propio

## Modelo de Datos

### Tabla: `users`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| username | VARCHAR(50) | UNIQUE, NOT NULL |
| password | VARCHAR(255) | NOT NULL (BCrypt hash) |
| email | VARCHAR(100) | UNIQUE, NOT NULL |
| full_name | VARCHAR(100) | NOT NULL |
| role | VARCHAR(20) | NOT NULL. Valores: SUPER_ADMIN, ADMINISTRATOR, SUPERVISOR, SELLER |
| active | BOOLEAN | NOT NULL, DEFAULT TRUE |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

**Constraints**: UK_users_username, UK_users_email

## Permisos
| Operación | Admin | Supervisor | Vendedor |
|---|---|---|---|
| Crear | ✓ | - | - |
| Leer | ✓ | - | - |
| Editar | ✓ | - | - |
| Eliminar | ✓ | - | - |
