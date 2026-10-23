<!-- spec-version: 1.2 | last-updated: 2026-10-02 -->
# Módulo: Autenticación (Auth)

## Dependencias
- **Requiere**: users
- **Requerido por**: todos los módulos (JWT es prerrequisito)

## Reglas de Negocio

### RN-AUTH-001 — Credenciales Cifradas
- Las contraseñas se almacenan con BCrypt (factor de costo ≥ 10). Nunca en texto plano.

### RN-AUTH-002 — Bloqueo por Intentos Fallidos
- 5 intentos fallidos consecutivos en 15 minutos → cuenta bloqueada temporalmente (30 min o intervención de Admin).

### RN-AUTH-003 — Refresh Token Rotativo con Detección de Reuso
- El login emite, además del JWT, un **refresh token** opaco (Base64URL) persistido **solo como hash SHA-256**.
- Cada `refresh` **rota** el token (single-use, bloqueo pesimista) dentro de la misma **familia** (`family_id`).
- Presentar un token ya rotado → se asume robo → se **revoca la familia completa** (RFC 6819).
- El logout revoca la familia del token presentado. Vida configurable via `JWT_REFRESH_EXPIRATION_MS` (7 días por defecto).

## Requerimientos Funcionales

### RF-AUTH-001 — Inicio de Sesión (Login)
- **Prioridad**: Alta (Bloqueante)
- **Criterios de Aceptación**:
  - Credenciales válidas → genera JWT y emite Cookie `HttpOnly` segura (`SameSite=Strict`, `Secure`, `Path=/`, `Max-Age=900`) junto con respuesta JSON de datos de sesión.
  - Credenciales inválidas → `401 Unauthorized`: "Usuario o contraseña incorrectos" (sin pistas).
  - Cuenta inactiva → `403 Forbidden`: "Cuenta de usuario desactivada".

### RF-AUTH-002 — Cierre de Sesión (Logout)
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Eliminar cookie `HttpOnly` del cliente enviando `Set-Cookie: jwt_token=; Max-Age=0; ...`
  - Limpiar metadatos cosméticos del cliente (`localStorage`/`sessionStorage`).
  - Peticiones posteriores sin token → denegar acceso (`401 Unauthorized` / `403 Forbidden`).

## Requerimientos No Funcionales

### RNF-AUTH-001 — HTTPS Obligatorio
- Toda transacción de autenticación debe realizarse sobre HTTPS.

### RNF-AUTH-002 — Tiempo de Expiración del Token
- El access token JWT expira a los 15 minutos desde emisión (configurable via `JWT_EXPIRATION_MS`). La sesión se mantiene con el refresh token.

## API Endpoints

### POST /api/v1/auth/login
- **Permisos**: Público (sin token)
- **Request**:
  ```json
  { "username": "admin", "password": "Password123" }
  ```
- **Response 200**:
  - **Headers**:
    - `Set-Cookie: jwt_token=eyJ...; Path=/; Max-Age=900; HttpOnly; SameSite=Strict`
    - `Set-Cookie: refresh_token=...; Path=/api/v1/auth; Max-Age=604800; HttpOnly; SameSite=Strict`
  - **Body**:
  ```json
  { "token": "eyJ...", "refreshToken": "...", "username": "admin", "role": "ADMINISTRATOR", "storeId": 1, "expiresIn": 900 }
  ```
- **Errores**: 401, 403

### POST /api/v1/auth/register
- **Permisos**: Público
- **Request**: `RegisterOwnerCommand` (datos del dueño).
- **Response**: `201 Created` sin cuerpo. Crea un usuario `ADMINISTRATOR` pendiente de verificar su correo y envía el enlace de verificación (proveedor de correo `EMAIL_PROVIDER`: `log` por defecto, SMTP en producción; vigencia del token `EMAIL_VERIFICATION_EXPIRATION_MS`, 24 h). Tras verificar, el dueño registra su local en `POST /api/v1/stores` (ver módulo `stores`).

### GET /api/v1/auth/verify-email?token=...
- **Permisos**: Público
- **Response 200**: consume el token, marca el correo como verificado y autologuea (mismo formato que login, setea ambas cookies).

### POST /api/v1/auth/resend-verification
- **Permisos**: Público
- **Request**: `{ "email": "..." }`
- **Response**: `204 No Content` siempre, exista o no la cuenta. Solo envía si la cuenta existe y sigue sin verificar; máximo un envío por correo por minuto (en memoria). Si el correo falla en `register`, la cuenta queda creada y el usuario usa este endpoint.

### POST /api/v1/backoffice/onboarding/pending-users/{userId}/resend-verification
- **Permisos**: `SUPER_ADMIN` (controlador en el módulo `backoffice`, que llega a `auth` solo por `AuthFacade.resendVerificationToUser`).
- **Response**: `204 No Content`. A diferencia del reenvío público, informa de los problemas: `400` si la cuenta ya está verificada o se envió otro correo hace menos de un minuto (el enfriamiento se comparte con el reenvío público), `409` si el proveedor de correo falla (un fallo no inicia el enfriamiento, así que se puede reintentar).

### POST /api/v1/auth/forgot-password
- **Permisos**: Público
- **Request**: `{ "email": "..." }`
- **Response**: `204 No Content` siempre. Envía un enlace `…/reset-password?token=…` (vigencia `SECURITY_PASSWORD_RESET_EXPIRATION_MS`, 1 h) solo si la cuenta está activa y con el correo verificado; máximo un envío por correo por minuto.

### POST /api/v1/auth/reset-password
- **Permisos**: Público
- **Request**: `{ "token": "...", "newPassword": "..." }`
- **Response**: `204 No Content`. Consume el token (un solo uso), cambia la contraseña, revoca todos los refresh tokens del usuario y levanta su bloqueo por intentos fallidos. Es transaccional: si la contraseña no cumple la política, el token no se consume.
- **Errores**: `400` con el motivo (enlace inválido, usado, expirado o contraseña débil). Se usa 400 y no 401 a propósito: el cliente interpreta cualquier 401 como sesión expirada.

### POST /api/v1/auth/refresh
- **Permisos**: Público (el access token puede estar expirado)
- **Request**: refresh token vía cookie `HttpOnly` o body `{ "refreshToken": "..." }`
- **Response 200**: mismo formato que login, con access + refresh token rotados (setea ambas cookies).
- **Errores**: `401` si el refresh token es inválido, expirado o reutilizado (reuso → revoca la sesión); `403` si el usuario fue desactivado.

### POST /api/v1/auth/logout
- **Permisos**: Cualquier rol autenticado
- **Request**: opcional `{ "refreshToken": "..." }` (por defecto lee la cookie)
- **Response**: `204 No Content` — revoca la familia del refresh token y limpia ambas cookies (`Max-Age=0`).

## Modelo de Datos
Tabla propia **`refresh_tokens`** (ver `_database-schema.md`): `id`, `token_hash` (SHA-256, único), `user_id` (FK→`users`), `family_id`, `expires_at`, `revoked`, `created_at`, `updated_at`. Las credenciales de usuario viven en la tabla `users` del módulo users.

## Permisos
| Operación | Admin | Supervisor | Vendedor |
|---|---|---|---|
| Login/Logout | ✓ | ✓ | ✓ |
