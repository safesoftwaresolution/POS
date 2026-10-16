<!-- spec-version: 1.2 | last-updated: 2026-10-16 -->
# Módulo: Autenticación (Auth)

## Dependencias
- **Requiere**: users, shared/email (envío del correo de verificación)
- **Requerido por**: todos los módulos (JWT es prerrequisito)

## Reglas de Negocio

### RN-AUTH-001 — Credenciales Cifradas
- Las contraseñas se almacenan con BCrypt (factor de costo 10). Nunca en texto plano.

### RN-AUTH-002 — Bloqueo por Intentos Fallidos
- 5 intentos fallidos consecutivos en 15 minutos → cuenta bloqueada **30 minutos** (`403`, no `429`). No existe desbloqueo por un administrador.
- **Limitación conocida**: el contador vive en memoria (`ConcurrentHashMap` en `AuthService`). Se pierde al reiniciar, no se comparte entre instancias y no se purga. No hay límite por IP ni rate limiting en `/register`, `/refresh` ni `/verify-email` (ver `_roadmap.md`).

### RN-AUTH-003 — Refresh Token Rotativo con Detección de Reuso
- El login emite, además del JWT, un **refresh token** opaco (Base64URL) persistido **solo como hash SHA-256**.
- Cada `refresh` **rota** el token (single-use, bloqueo pesimista) dentro de la misma **familia** (`family_id`).
- Presentar un token ya rotado → se asume robo → se **revoca la familia completa** (RFC 6819), **salvo** dentro de la ventana de gracia `security.jwt.refresh-reuse-grace-ms` (`JWT_REFRESH_REUSE_GRACE_MS`, 15 s por defecto): dos pestañas o un reintento de red que presentan el mismo token justo después de rotarlo no cuentan como robo y no revocan la familia.
- El logout revoca la familia del token presentado. Vida configurable vía `JWT_REFRESH_EXPIRATION_MS` (7 días por defecto). La vida se reinicia en cada rotación: no hay caducidad absoluta de la sesión.

### RN-AUTH-004 — Verificación de Correo en el Auto-registro
- `POST /auth/register` crea un `ADMINISTRATOR` **sin local** (`store_id = null`) y con `email_verified = false`, y envía un enlace con un token de un solo uso (hash SHA-256 en `email_verification_tokens`, vigencia `EMAIL_VERIFICATION_EXPIRATION_MS`, 24 h por defecto).
- Mientras el correo no está verificado, el login responde `403` ("verifica tu correo").
- `GET /auth/verify-email?token=` consume el token, marca `email_verified = true` y **autologuea** (setea ambas cookies).

### RN-AUTH-005 — Administrador sin local
- Un `ADMINISTRATOR` con `storeId = null` solo puede usar `/api/v1/auth/**` y registrar su local (`POST /api/v1/stores`, `GET/PUT /api/v1/stores/me*`). Cualquier otra ruta responde `403 StoreOnboardingRequired` desde `JwtAuthenticationFilter`.

## Requerimientos Funcionales

### RF-AUTH-001 — Inicio de Sesión (Login)
- **Prioridad**: Alta (Bloqueante)
- **Criterios de Aceptación**:
  - Credenciales válidas → genera JWT y emite cookies `HttpOnly` (`SameSite=Strict`, `Path=/`, `Max-Age=900`) junto con la respuesta JSON de sesión. El atributo `Secure` lo gobierna `security.jwt.cookie-secure` (`JWT_COOKIE_SECURE`), que **por defecto es `false`**: en producción debe activarse.
  - Credenciales inválidas → `401 Unauthorized`: "Usuario o contraseña incorrectos" (sin pistas).
  - Cuenta inactiva → `403 Forbidden`: "Cuenta de usuario desactivada".
  - Correo sin verificar → `403 Forbidden`.

### RF-AUTH-002 — Cierre de Sesión (Logout)
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Revoca la familia del refresh token y limpia ambas cookies (`Max-Age=0`).
  - El endpoint exige un access token válido: con el token expirado el refresh token **no** se revoca (ver `_roadmap.md`).

## Requerimientos No Funcionales

### RNF-AUTH-001 — HTTPS Obligatorio
- Toda transacción de autenticación debe realizarse sobre HTTPS.

### RNF-AUTH-002 — Tiempo de Expiración del Token
- El access token JWT expira a los 15 minutos desde emisión (`JWT_EXPIRATION_MS`). La sesión se mantiene con el refresh token.

## API Endpoints

### POST /api/v1/auth/login
- **Permisos**: Público (sin token)
- **Request**: `{ "username": "admin", "password": "Password123" }`
- **Response 200**:
  - **Headers**: `Set-Cookie: jwt_token=…; Path=/; Max-Age=900; HttpOnly; SameSite=Strict` y `Set-Cookie: refresh_token=…; Path=/api/v1/auth; Max-Age=604800; HttpOnly; SameSite=Strict`
  - **Body**: `{ "token": "eyJ...", "refreshToken": "...", "username": "admin", "role": "ADMINISTRATOR", "storeId": 1, "expiresIn": 900 }`
- **Errores**: 401, 403

### POST /api/v1/auth/register
- **Permisos**: Público
- **Request**: `RegisterOwnerCommand { fullName, username, email, password }`
- **Response**: `201 Created` sin cuerpo (se envía el correo de verificación). **Errores**: 400 (usuario/correo duplicado, contraseña débil).

### GET /api/v1/auth/verify-email?token=
- **Permisos**: Público
- **Response 200**: igual que login (autologuea). **Errores**: 400/401 si el token es inválido, usado o expirado.

### POST /api/v1/auth/refresh
- **Permisos**: Público (el access token puede estar expirado)
- **Request**: refresh token vía cookie `HttpOnly` o body `{ "refreshToken": "..." }`
- **Response 200**: mismo formato que login, con access + refresh token rotados (setea ambas cookies).
- **Errores**: `401` si el refresh token es inválido, expirado o reutilizado fuera de la ventana de gracia (reuso → revoca la sesión); `403` si el usuario fue desactivado.

### POST /api/v1/auth/logout
- **Permisos**: Cualquier rol autenticado
- **Request**: opcional `{ "refreshToken": "..." }` (por defecto lee la cookie)
- **Response**: `204 No Content`.

## Modelo de Datos
- **`refresh_tokens`** (`V4`): `id`, `token_hash` (SHA-256, único), `user_id` (FK→`users`), `family_id`, `expires_at`, `revoked`, `created_at`, `updated_at`.
- **`email_verification_tokens`** (`V7`): `id`, `token_hash` (único), `user_id` (FK→`users`), `expires_at`, `used_at`, `created_at`.
- Las credenciales viven en `users` (módulo users); `users.email_verified` es de `V7`.

## Permisos
| Operación | Público | Cualquier rol autenticado |
|---|---|---|
| Login / Registro / Verificar correo / Refresh | ✓ | ✓ |
| Logout | - | ✓ |
