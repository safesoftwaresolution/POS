<!-- doc-version: 1.5 | last-updated: 2026-10-02 -->
# Seguridad del Sistema

## Autenticación (JWT + Refresh Token)
- **Access token**: JWT firmado con HMAC-SHA256, stateless, de vida corta (`security.jwt.expiration-ms`, 15 minutos por defecto).
- **Refresh token**: valor opaco aleatorio (Base64URL, 48 bytes) de vida larga (`security.jwt.refresh-expiration-ms`, 7 días por defecto). Se persiste **solo como hash SHA-256** en la tabla `refresh_tokens` (referenciada a `users` por `user_id`), por lo que es revocable y una fuga de la tabla no permite reconstruir tokens válidos.
- **Rotación (single-use)**: cada llamada a `POST /api/v1/auth/refresh` revoca el refresh token usado (con bloqueo pesimista para serializar rotaciones concurrentes) y emite uno nuevo dentro de la misma **familia** (`family_id`).
- **Detección de reuso (RFC 6819)**: si se presenta un refresh token ya rotado (revocado), se asume robo y se **revoca la familia completa** (toda la sesión), no solo ese token. Un usuario desactivado durante una sesión activa también provoca la revocación de su familia en el siguiente `refresh`.
- **Ciclo de vida**: en cada login se purgan los tokens caducados/revocados del usuario para acotar el crecimiento de la tabla.
- **Endpoints públicos**:
  - `POST /api/v1/auth/login` — emite access + refresh token (body y cookies `HttpOnly`).
  - `POST /api/v1/auth/refresh` — renueva el access token a partir del refresh token (cookie `HttpOnly` o campo `refreshToken` en el body).
  - `POST /api/v1/auth/register` — auto-registro del dueño (crea un `ADMINISTRATOR` pendiente de verificar correo).
  - `GET /api/v1/auth/verify-email?token=...` — verifica el correo y autologuea.
  - `POST /api/v1/auth/resend-verification` — reenvía el enlace de verificación; responde `204` siempre (no revela qué correos existen) y limita a un envío por correo por minuto.
  - `POST /api/v1/auth/forgot-password` — envía un enlace de recuperación (un solo uso, 1 h) solo a cuentas activas con correo verificado; mismas garantías (`204` siempre, 1 envío/min).
  - `POST /api/v1/auth/reset-password` — consume el token, fija la nueva contraseña (misma política de fortaleza), **revoca todas las sesiones** del usuario y limpia su bloqueo por intentos. Tokens: opacos, solo se guarda su SHA-256 (`password_reset_tokens`).
  - `GET /api/v1/legal/{slug}` (lectura pública de políticas y términos)
  - `POST /api/v1/support/tickets` (radicación pública de PQRs y bugs)
  - `GET /api/v1/support/tickets/track/{ticketNumber}` (tracking público)
- **Cierre de sesión**: `POST /api/v1/auth/logout` revoca el refresh token asociado y limpia ambas cookies.
- **Cookies**: `jwt_token` (path `/`) para el access token y `refresh_token` (path `/api/v1/auth`, alcance restringido a los endpoints de sesión), ambas `HttpOnly`.
- **Payload JWT**: `sub` (username), `role` (SUPER_ADMIN, ADMINISTRATOR, SUPERVISOR, SELLER), `storeId` (Long, null para SUPER_ADMIN), `iat`, `exp`

## Roles del Sistema (RBAC)
- **SUPER_ADMIN**: Equipo interno de la plataforma SaaS. Control total sobre Back Office (`/api/v1/backoffice/**`) y gestión exclusiva de categorías (`/api/v1/categories` POST, PUT, DELETE). No pertenece a ningún local (`store_id = null`).
- **ADMINISTRATOR**: Administrador/Dueño de un local específico.
- **SUPERVISOR**: Encargado de tienda de un local específico.
- **SELLER**: Vendedor / cajero de un local específico.

### Matriz de Permisos
| Módulo | SUPER_ADMIN | Admin Local | Supervisor | Vendedor | Público |
|---|---|---|---|---|---|
| Back Office (Locales / Métricas) | CRUD | - | - | - | - |
| Categorías (Globales) | CRUD | R | R | R | - |
| Textos Legales | CRUD / Publicar | R | R | R | R (Vigente) |
| Soporte / PQRs / Bugs | CRUD / Resolver | R (Propios) | - | - | Crear / R (Radicado) |
| Usuarios Local | CRUD | CRUD (Local) | - | - | - |
| Productos Local | R | CRUD | CRU | R | - |
| Ventas Local | R | CRUD | CRUD | CRU | - |
| Credenciales de Facturación Electrónica | CRUD | - | - | - | - |
| Estado de Facturación del propio local | - | R / Solicitar | R | - | - |
| Rangos de Numeración DIAN | - | CRUD | R | - | - |

## Credenciales de Facturación Electrónica (Factus)

Las credenciales que el proveedor entrega por local son el secreto más sensible del sistema después del
`JWT_SECRET`: con ellas se emiten documentos legales ante la DIAN consumiendo la bolsa de un local concreto.

- **Custodia**: solo el **SUPER_ADMIN** puede cargarlas
  (`PUT /api/v1/backoffice/stores/{storeId}/billing/credentials`). El administrador del local nunca las ve ni
  las envía: ningún endpoint bajo `/api/v1/billing/activation` las acepta ni las devuelve.
- **Cifrado en reposo**: `client_secret` y `password` se guardan cifrados con **AES-256-GCM**
  (`SecretCryptoConverter`, un `AttributeConverter` de JPA). Formato: `v1:` + Base64(IV de 12 bytes ‖ cifrado ‖
  etiqueta GCM). Una fuga de la tabla `store_billing_config` no expone las credenciales.
- **Clave de cifrado**: `BILLING_ENCRYPTION_KEY` (32 bytes en Base64), única pieza fuera de la base de datos.
  Perderla o cambiarla inutiliza los secretos ya guardados. Generación en Windows/PowerShell:
  `$b=New-Object byte[] 32; [Security.Cryptography.RNGCryptoServiceProvider]::new().GetBytes($b); [Convert]::ToBase64String($b)`.
- **Sin credenciales globales**: desde 2026-09-22 no existen variables de entorno con credenciales de Factus
  (`FACTUS_CLIENT_ID`, `FACTUS_CLIENT_SECRET`, `FACTUS_USERNAME`, `FACTUS_PASSWORD`,
  `FACTUS_NUMBERING_RANGE_ID` fueron eliminadas). Un local sin credenciales propias **no factura**: no hay
  respaldo a una cuenta de plataforma que pudiera consumir la bolsa de otro local.
- **Aislamiento entre locales**: el token OAuth2 se cachea por (`store_id`, entorno) y se invalida al cambiar
  las credenciales o ante un `401`. Ninguna llamada a Factus se hace sin resolver antes el local dueño.
- **Nunca en logs ni en respuestas**: `FactusAccess.toString()` omite los secretos y `BillingActivationDto`
  solo informa si cada entorno está completo, nunca el valor.
- **Auditoría**: `store_billing_config.updated_by` guarda quién tocó la configuración por última vez, y
  `numbering_range_audit` registra cada cambio sobre los rangos de numeración (acción, valor anterior y nuevo,
  usuario y fecha).