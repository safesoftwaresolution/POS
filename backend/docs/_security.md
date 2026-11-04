<!-- doc-version: 1.5 | last-updated: 2026-10-16 -->
# Seguridad del Sistema

## Autenticación (JWT + Refresh Token)
- **Access token**: JWT HMAC, stateless, de vida corta (`security.jwt.expiration-ms`, 15 minutos por defecto). jjwt elige HS256/384/512 según la longitud del secreto (`Keys.hmacShaKeyFor`); el algoritmo no se fija explícitamente. El secreto (`JWT_SECRET`) debe tener ≥ 32 bytes; hoy no se valida al arrancar.
- **Refresh token**: valor opaco aleatorio (Base64URL, 48 bytes) de vida larga (`security.jwt.refresh-expiration-ms`, 7 días). Se persiste **solo como hash SHA-256** en `refresh_tokens` (por `user_id`), por lo que es revocable y una fuga de la tabla no permite reconstruir tokens.
- **Rotación (single-use)**: cada `POST /api/v1/auth/refresh` revoca el token usado (con bloqueo pesimista) y emite uno nuevo dentro de la misma **familia** (`family_id`). La vida se reinicia en cada rotación (no hay caducidad absoluta).
- **Detección de reuso (RFC 6819)**: presentar un refresh token ya rotado se interpreta como robo y **revoca la familia completa**, **salvo** dentro de la ventana de gracia `security.jwt.refresh-reuse-grace-ms` (15 s por defecto), pensada para dos pestañas o un reintento de red. Un usuario desactivado durante una sesión también provoca la revocación de su familia.
- **Ciclo de vida**: en cada login se purgan los tokens caducados/revocados del usuario.
- **Frontend**: los tokens viven solo en memoria; `localStorage` guarda únicamente el perfil. La sesión real son las cookies `HttpOnly`, que el JavaScript no puede leer (verificado: la API responde con solo la cookie y `refresh` funciona por cookie).
- **Cierre de sesión**: `POST /api/v1/auth/logout` revoca el refresh token asociado y limpia ambas cookies. Exige un access token válido: con el token expirado no revoca.
- **Cookies**: `jwt_token` (path `/`) y `refresh_token` (path `/api/v1/auth`), ambas `HttpOnly`, `SameSite=Strict`. El atributo `Secure` lo controla `security.jwt.cookie-secure` (`JWT_COOKIE_SECURE`), **`false` por defecto**: activarlo en producción.
- **Payload JWT**: `sub` (username), `role`, `storeId` (null para SUPER_ADMIN), `iat`, `exp`. El rol efectivo se toma de la BD en cada solicitud.
- **Administrador sin local**: un `ADMINISTRATOR` con `storeId = null` solo accede a `/api/v1/auth/**` y al registro de su local; el resto responde `403 StoreOnboardingRequired`.

### Endpoints públicos (sin token)
Definidos en `SecurityConfig`:
- `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/register`, `GET /api/v1/auth/verify-email`
- `POST /api/v1/auth/resend-verification` — reenvía el enlace de verificación; responde `204` siempre (no revela qué correos existen) y limita a un envío por correo por minuto.
- `POST /api/v1/auth/forgot-password` — envía un enlace de recuperación (un solo uso, 1 h) solo a cuentas activas con correo verificado; mismas garantías (`204` siempre, 1 envío/min).
- `POST /api/v1/auth/reset-password` — consume el token, fija la nueva contraseña (misma política de fortaleza), **revoca todas las sesiones** del usuario y limpia su bloqueo por intentos. Tokens: opacos, solo se guarda su SHA-256 (`password_reset_tokens`, V19).
- `GET /api/v1/legal/**` (políticas y términos vigentes)
- `POST /api/v1/support/tickets` y `GET /api/v1/support/tickets/track/**` (el seguimiento devuelve solo estado y resolución, no datos de contacto)
- `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` (abierta en `dev`; en el perfil `prod` está **apagada** salvo `SWAGGER_ENABLED=true`)
- Cualquier ruta que **no** empiece por `/api/v1/` también queda abierta (`anyRequest().permitAll()`), por ejemplo `/h2-console` en `dev`.

### CORS
`SecurityConfig.corsConfigurationSource` usa una **lista explícita de orígenes** (`app.cors.allowed-origins`; por defecto `APP_FRONTEND_URL`) con `allowCredentials(true)`. Ya no se refleja cualquier `Origin` (antes `allowedOriginPatterns("*")`). En producción debe coincidir con la URL pública del SPA.

### CSRF
Deshabilitado; la sesión viaja en cookies, y la mitigación es `SameSite=Strict` más CORS cerrado. Si se relaja cualquiera de los dos, hay que reintroducir protección CSRF.

### Bloqueo y límites
- **Bloqueo de login**: 5 intentos fallidos en 15 minutos → 30 min de bloqueo (`403`). El contador está en memoria (se pierde al reiniciar, no se comparte entre instancias).
- **No hay rate limiting** en `/register`, `/refresh`, `/verify-email` ni `/support/tickets`. Pendiente (`_roadmap.md`). `/resend-verification` y `/forgot-password` solo limitan a un envío por correo por minuto, con el contador en memoria.
- **Contraseñas**: BCrypt costo 10; mínimo 8 caracteres con una letra y un número.

## Roles del Sistema (RBAC)
- **SUPER_ADMIN**: equipo de la plataforma. Control total del Back Office (`/api/v1/backoffice/**`) y único que escribe las categorías globales. Sin local (`store_id = null`). Solo se crea desde `/backoffice/staff` o por el seeder; **ningún endpoint del local puede asignarlo**.
- **ADMINISTRATOR**: dueño de un local.
- **SUPERVISOR**: encargado de tienda.
- **SELLER**: vendedor / cajero.

### Matriz de Permisos (verificada contra los `@PreAuthorize`)
| Módulo | SUPER_ADMIN | Administrador | Supervisor | Vendedor | Público |
|---|---|---|---|---|---|
| Back Office (locales, KYC, métricas, staff, onboarding) | CRUD | - | - | - | - |
| Registro/consulta del propio local (`/api/v1/stores/**`) | - | ✓ | - | - | - |
| Categorías (globales) | CRUD | R | R | R | - |
| Subcategorías (por local) | - | CRUD | CRUD | R | - |
| Textos Legales | CRUD / Publicar | - | - | - | R (vigentes) |
| Soporte / PQRs | CRUD / Resolver | - | - | - | Crear / R estado por radicado |
| Usuarios del local | - (usa staff) | CRUD | - | - | - |
| Productos | - | CRUD | CRU (sin borrar) | R | - |
| Clientes | - | CRUD | CRU | CRU | - |
| Proveedores | - | CRUD | CRU | - | - |
| Compras | - | C R | C R | - | - |
| Inventario (movimientos) | - | C R | C R | R | - |
| Ventas / devoluciones | - | C R | C R | C R | - |
| Reportes | - | Todos | Solo stock | - | - |
| Configuración del negocio | - | R / U | R | - | - |
| Catálogos de parámetros | CRUD | R | R | R | - |
| Logo del negocio | - | ✓ | - | - | - |
| Storage (subir / borrar archivos) | - | ✓ | ✓ | - | - |
| Notificaciones | - | ✓ | ✓ | ✓ | - |
| Facturas, notas crédito/débito | - | ✓ | ✓ | Consultar / descargar | - |
| Rangos de Numeración DIAN | - | CRUD | R | - | - |
| Credenciales de Facturación Electrónica | CRUD | - | - | - | - |
| Estado de Facturación del propio local | R (cualquiera) | R / Solicitar | R | - | - |

El detalle por operación está en el `spec.md` de cada módulo. Salvo los endpoints públicos, **todo endpoint debe llevar `@PreAuthorize`**: sin él queda abierto a cualquier usuario autenticado (así estuvo `storage` y `PATCH /users/{id}/password`; este último sigue sin anotación y aplica su regla en el servicio).

## Aislamiento entre locales
Cada solicitud lleva el `storeId` en `TenantContext`. El aislamiento es manual en cada servicio (no hay `@Filter` de Hibernate ni RLS). Reglas vigentes:
- Un recurso de otro local se trata como inexistente (404), no como prohibido.
- Las operaciones de facturación y archivos **exigen** local; no existe respaldo al local 1.
- Los archivos de R2 subidos por el endpoint HTTP van bajo `{carpeta}/tienda-{storeId}/` (ej. `imagenes-productos/tienda-4/`) y solo se borran claves de ese prefijo (o del prefijo antiguo `tienda-{storeId}/`).

## Datos sembrados
`DatabaseSeeder` ya no crea cuentas con contraseña conocida en producción: el `SUPER_ADMIN` solo se siembra si se define `SEED_SUPERADMIN_PASSWORD`, y `admin`/local 1 solo en el perfil `dev`. Cualquier entorno que haya arrancado antes con la versión anterior puede tener `superadmin / SuperAdmin123!` y `admin / Password123`: **rotarlas**.

## Credenciales de Facturación Electrónica (Factus)

Las credenciales que el proveedor entrega por local son el secreto más sensible del sistema después del
`JWT_SECRET`: con ellas se emiten documentos legales ante la DIAN consumiendo la bolsa de un local concreto.

- **Custodia**: solo el **SUPER_ADMIN** puede cargarlas
  (`PUT /api/v1/backoffice/stores/{storeId}/billing/credentials`). El administrador del local nunca las ve ni
  las envía: ningún endpoint bajo `/api/v1/billing/activation` las acepta ni las devuelve.
- **Cifrado en reposo**: `client_secret` y `password` se guardan cifrados con **AES-256-GCM**
  (`SecretCryptoConverter`, un `AttributeConverter` de JPA). Formato: `v1:` + Base64(IV de 12 bytes ‖ cifrado ‖
  etiqueta GCM). Una fuga de la tabla `store_billing_config` no expone las credenciales. Limitaciones: la clave se valida de forma perezosa y `v1:` no lleva identificador de clave, así que no hay rotación.
- **Clave de cifrado**: `BILLING_ENCRYPTION_KEY` (32 bytes en Base64), única pieza fuera de la base de datos.
  Perderla o cambiarla inutiliza los secretos ya guardados. Generación en Windows/PowerShell:
  `$b=New-Object byte[] 32; [Security.Cryptography.RNGCryptoServiceProvider]::new().GetBytes($b); [Convert]::ToBase64String($b)`.
- **Sin credenciales globales**: no existen variables de entorno con credenciales de Factus. Un local sin
  credenciales propias **no factura**: no hay respaldo a una cuenta de plataforma que pudiera consumir la bolsa de otro local.
- **Aislamiento entre locales**: el token OAuth2 se cachea por (`store_id`, entorno) y se invalida al cambiar
  las credenciales o ante un `401`. Ninguna llamada a Factus se hace sin resolver antes el local dueño.
- **Nunca en logs ni en respuestas**: `FactusAccess.toString()` omite los secretos y `BillingActivationDto`
  solo informa si cada entorno está completo, nunca el valor.
- **Auditoría**: `store_billing_config.updated_by` guarda quién tocó la configuración por última vez, y
  `numbering_range_audit` registra cada cambio sobre los rangos de numeración (acción, valor anterior y nuevo,
  usuario y fecha).
- **Proveedor y timeouts**: `billing.provider` desconocido falla (sin respaldo a `mock`) y las llamadas salientes tienen timeouts (ver `modules/billing/spec.md`, RN-BILL-017/018).
