<!-- spec-version: 1.1 | last-updated: 2026-10-16 -->
# Módulo: Usuarios (Users)

## Dependencias
- **Requiere**: customers, settings (solo `DatabaseSeeder`, para sembrar el local de desarrollo)
- **Requerido por**: auth, sales, purchases, inventory, stores, backoffice

## Reglas de Negocio

### RN-USER-001 — Roles Estáticos y Fijos
- Existen cuatro roles: `SUPER_ADMIN` (plataforma, sin local), `ADMINISTRATOR`, `SUPERVISOR` y `SELLER` (los tres, siempre con local). No hay CRUD de roles.
- **`SUPER_ADMIN` no se asigna por los endpoints del local**: `createUser` lo rechaza cuando hay un local en contexto y `updateUser` lo rechaza siempre que el usuario no fuera ya `SUPER_ADMIN`. Solo se crea desde el back office (`/api/v1/backoffice/staff`) o por el seeder. Un usuario sin local solo puede ser `SUPER_ADMIN`, y un `SUPER_ADMIN` nunca lleva local.

### RN-USER-002 — Autoconservación del Administrador
- No se puede eliminar ni **desactivar** al último `ADMINISTRATOR` activo de un local, ni cambiarle el rol. **Reactivar** a un administrador siempre está permitido.
- No hay protección equivalente para el último `SUPER_ADMIN` (ver `_roadmap.md`).

### RN-USER-003 — Alcance por Local
- Todos los endpoints operan sobre los usuarios del local de la sesión (`TenantContext`); un usuario de otro local se trata como inexistente (404). Sin local en contexto (back office, seeder) no hay restricción.

### RN-USER-004 — Siembra de Cuentas
- `DatabaseSeeder` crea el `SUPER_ADMIN` inicial **solo si** se define `SEED_SUPERADMIN_PASSWORD` (`app.seed.superadmin-password`); en `dev` tiene un valor por defecto. El administrador de prueba `admin`, el local 1, el "Cliente General" y los ajustes de ejemplo se siembran **solo en el perfil `dev`**. Ninguna contraseña se imprime por consola.

## Requerimientos Funcionales

### RF-USER-001 — Crear Usuario
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Campos obligatorios: fullName, username (único), password, role; email (único) con formato válido. Se valida con Bean Validation (`@Valid`) → 400.
  - El nuevo usuario pertenece siempre al local del solicitante.
  - Estado inicial: Activo.
  - Contraseña: mínimo 8 caracteres, al menos 1 letra y 1 número.

### RF-USER-002 — Editar Usuario
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Editable: fullName, email, role (nunca a `SUPER_ADMIN`, RN-USER-001).
  - No editable: username.
  - Validar email no duplicado.

### RF-USER-003 — Activar/Desactivar Usuario
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Admin puede desactivar usuario → revoca acceso inmediato.
  - Admin NO puede desactivarse a sí mismo.
  - Sesiones activas se invalidan en la siguiente petición.

### RF-USER-004 — Eliminar Usuario (Borrado Lógico)
- **Prioridad**: Media
- **Criterios de Aceptación**:
  - Siempre es borrado lógico (`active = false`). No aparece en listados activos.
  - **Limitación**: el listado solo devuelve activos, así que un usuario desactivado no se puede ver ni reactivar desde la API.

### RF-USER-005 — Restablecer Contraseña
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - El propio usuario cambia su contraseña proporcionando la actual.
  - Un `ADMINISTRATOR` (o `SUPER_ADMIN`) restablece la de cualquier usuario de su ámbito.
  - Un `SUPERVISOR` solo restablece la de usuarios `SELLER`; sobre un `ADMINISTRATOR` o `SUPERVISOR` responde 403 (evita tomar su cuenta).
  - **Pendiente**: no se revocan los refresh tokens del usuario afectado al cambiar su contraseña.

## Requerimientos No Funcionales

### RNF-USER-001 — Encriptación BCrypt
- Contraseñas hasheadas con BCrypt (costo 10).

## API Endpoints

### POST /api/v1/users
- **Permisos**: Administrador
- **Request**: `{ "fullName": "Juan Perez", "username": "juanp", "email": "juan@tienda.com", "password": "TemporalPassword1", "role": "SELLER" }`
- **Response 201**: `{ "id": 5, "fullName": "Juan Perez", "username": "juanp", "email": "juan@tienda.com", "role": "SELLER", "active": true, "storeId": 1 }`
- **Errores**: 400 (campos inválidos, username/email duplicado, contraseña débil, rol `SUPER_ADMIN`)

### GET /api/v1/users
- **Permisos**: Administrador
- **Response 200**: Lista paginada de usuarios activos del local

### GET /api/v1/users/{id}
- **Permisos**: Administrador. **Errores**: 404

### PUT /api/v1/users/{id}
- **Permisos**: Administrador
- **Errores**: 400 (incluye intento de asignar `SUPER_ADMIN`), 404

### PATCH /api/v1/users/{id}/status
- **Permisos**: Administrador. Activa/desactiva usuario.

### DELETE /api/v1/users/{id}
- **Permisos**: Administrador. Borrado lógico.

### PATCH /api/v1/users/{id}/password
- **Permisos**: usuario propio (con la contraseña actual); Administrador sobre cualquiera; Supervisor solo sobre `SELLER`.
- **Nota**: el endpoint no lleva `@PreAuthorize`; la regla vive en `UserServiceImpl.changePassword`.

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
| store_id | BIGINT | FK → stores(id); NULL solo para SUPER_ADMIN y administradores recién registrados (`V3`) |
| email_verified | BOOLEAN | NOT NULL, DEFAULT TRUE (`V7`) |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

**Constraints**: UK_users_username, UK_users_email. **Índices**: `IDX_users_store`, `IDX_users_email_verified`.

## Permisos
| Operación | SUPER_ADMIN | Admin | Supervisor | Vendedor |
|---|---|---|---|---|
| Crear / Leer / Editar / Eliminar usuarios del local | - (usa `/backoffice/staff`) | ✓ | - | - |
| Restablecer contraseña de otro | ✓ (vía back office) | ✓ | Solo de SELLER | - |
| Cambiar la propia contraseña | ✓ | ✓ | ✓ | ✓ |
