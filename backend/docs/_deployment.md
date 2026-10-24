<!-- doc-version: 1.2 | last-updated: 2026-10-16 -->
# Despliegue y Configuración

## Requisitos del Servidor
| Componente | Mínimo | Recomendado |
|---|---|---|
| SO | Linux Ubuntu 22.04 / Windows Server 2022 | Ubuntu 22.04 LTS |
| JDK | 21 LTS | Amazon Corretto 21 |
| CPU/RAM | 1 vCPU / 2 GB | 2 vCPU / 4 GB |
| Disco | 10 GB | 20 GB |
| BD | PostgreSQL 16 | PostgreSQL 16, 5 GB inicial |
| Node (solo para construir el frontend) | 20.19+ o 22.12+ | 22 LTS (lo exige Vite 8) |

## Variables de Entorno
Todas se leen con `${VAR:default}` desde `application*.properties`; en local se cargan desde `backend/.env` (ver `.env.example`).

| Variable | Descripción | Por defecto / notas |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil (`dev`, `prod`, `test`) | `dev` si no se define. **En producción debe ser `prod`**: `dev` activa la consola H2 y un secreto JWT público |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Conexión a la BD | `jdbc:postgresql://localhost:5432/app_db`; `dev` usa H2 en archivo `./data/app_db` |
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Solo para Docker Compose (Postgres) | |
| `JWT_SECRET` | Clave HMAC del JWT (≥ 32 bytes) | **Obligatoria fuera de `dev`** (secreto) |
| `JWT_EXPIRATION_MS` | TTL del access token | 900000 (15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | TTL del refresh token | 604800000 (7 días) |
| `JWT_REFRESH_REUSE_GRACE_MS` | Ventana de gracia ante reuso del refresh token | 15000 |
| `JWT_COOKIE_SECURE` | Atributo `Secure` de las cookies | `false`; **poner `true` en producción (HTTPS)** |
| `JWT_COOKIE_SAME_SITE` / `JWT_COOKIE_NAME` / `JWT_REFRESH_COOKIE_NAME` | Cookies de sesión | `Strict` / `jwt_token` / `refresh_token` |
| `APP_FRONTEND_URL` | URL pública del SPA (enlaces de correo y **origen CORS permitido**) | `http://localhost:3000` |
| `APP_CORS_ALLOWED_ORIGINS` | Lista de orígenes CORS separados por coma | por defecto, `APP_FRONTEND_URL` |
| `EMAIL_PROVIDER` | `log` (solo escribe en el log) o `smtp` | `log`: con él **nadie recibe el correo de verificación** |
| `EMAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Envío SMTP | |
| `EMAIL_VERIFICATION_EXPIRATION_MS` | Vigencia del enlace de verificación | 86400000 (24 h) |
| `BILLING_PROVIDER` | `factus` o `mock`. Un valor desconocido **falla** (sin respaldo a `mock`) | `factus` |
| `BILLING_ENCRYPTION_KEY` | Clave AES-256 (32 bytes en Base64) que cifra las credenciales de Factus de cada local | Sin valor por defecto; no perderla ni cambiarla |
| `FACTUS_URL_SANDBOX` / `FACTUS_URL_PRODUCTION` | URLs públicas de Factus | `https://api-sandbox.factus.com.co` / `https://api.factus.com.co` |
| `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET_NAME`, `R2_PUBLIC_URL` | Cloudflare R2 (módulo `storage`) | |
| `SEED_SUPERADMIN_PASSWORD` | Contraseña del `SUPER_ADMIN` inicial. Sin ella no se siembra | solo `dev` tiene valor por defecto |
| `SPRING_FLYWAY_ENABLED`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SPRING_H2_CONSOLE_ENABLED` | Overrides de infraestructura | Flyway activo; `validate`; consola H2 solo en `dev` |

No existen variables `FACTUS_CLIENT_ID/SECRET/USERNAME/PASSWORD` (las credenciales de Factus son por local y viven cifradas en la BD), ni `PORT`, `DB_HOST`, `DB_PORT`, `DB_USERNAME` ni `UPLOAD_DIR` (los archivos van a R2).

> `docker-compose.prod.yml` reenvía todas estas variables. Son **obligatorias** `DB_PASSWORD`, `JWT_SECRET` y `APP_FRONTEND_URL`; `JWT_COOKIE_SECURE` es `true` y `EMAIL_PROVIDER` es `smtp` por defecto (ponlos a `false`/`log` solo para probar sin HTTPS ni SMTP). Docker Compose lee además `backend/.env`: si ese archivo define `JWT_COOKIE_SECURE=false` (como `.env.example`), gana sobre el valor por defecto.

## Compilación Tradicional (Nativa)
```bash
./mvnw clean package -DskipTests
```
Genera: `target/app.pos-0.0.1-SNAPSHOT.jar`

## Ejecución Tradicional (Nativa)
```bash
java -jar target/app.pos-0.0.1-SNAPSHOT.jar
```

## Inicialización de BD
- Esquema: **lo crea Flyway** (`db/migration`, hoy `V1`…`V24`) y Hibernate solo lo valida (`ddl-auto=validate`) en `dev` y `prod`. Nunca `update`. Solo el perfil `test` deja que Hibernate cree el esquema.
- `dev` persiste en `./data/app_db` (borra `backend/data/` para empezar de cero).
- Datos semilla: el `SUPER_ADMIN` inicial solo si se define `SEED_SUPERADMIN_PASSWORD`. El administrador `admin`, el local 1, el "Cliente General" y los ajustes de ejemplo se siembran **únicamente en `dev`**, igual que los productos de ejemplo (`ProductDemoSeeder`). En cualquier perfil se siembran, de forma idempotente, las categorías de producto base (`CategorySeeder`) y los tipos de negocio de los locales (`StoreCategorySeeder`). En producción cada local se crea por el flujo de auto-registro y onboarding.

---

## Despliegue Containerizado (Docker y Docker Compose)

Construcción multi-etapa en el `Dockerfile` (etapas `dev`, `builder` y `prod`).

### 1. Entorno de Desarrollo (Local)
Etapa `dev`: monta el código fuente y el repositorio Maven como volúmenes y habilita el reinicio en caliente (DevTools). La base es PostgreSQL expuesta en el puerto 5434.

- Levantar servicios (PowerShell): `backend\docker-dev.ps1 start`
- Levantar servicios (Unix Bash): `backend/docker-dev.sh start`
- Detener servicios: `docker compose down`

> `docker-compose.yml` monta `/app/target` como volumen anónimo, de modo que el `target/` del host queda enmascarado; la recarga ocurre dentro del contenedor.

### 2. Entorno de Producción
Etapa `prod`: construye el JAR dentro del contenedor y genera una imagen JRE ligera, sin código fuente, ejecutando como el usuario no privilegiado `spring`.

```bash
docker compose -f docker-compose.prod.yml up --build -d
docker compose -f docker-compose.prod.yml down
```

### Transición de Desarrollo a Producción
1. **Sin dependencias del host**: el empaquetado final ocurre en la etapa `builder`.
2. **Variables de entorno**: define al menos `SPRING_PROFILES_ACTIVE=prod`, `DB_*`, `JWT_SECRET`, `JWT_COOKIE_SECURE=true`, `APP_FRONTEND_URL`, `EMAIL_PROVIDER=smtp` + `SMTP_*`, `BILLING_ENCRYPTION_KEY`, `R2_*` y `SEED_SUPERADMIN_PASSWORD`.
3. **Hibernate**: `validate` en todos los entornos; el esquema solo cambia con migraciones Flyway.
4. **Sirve el SPA detrás de HTTPS** (proxy inverso): las cookies de sesión y el CORS dependen de ello.
5. La imagen `prod` fija la zona horaria `America/Bogota` (`TZ` y `-Duser.timezone`), trae un `HEALTHCHECK` TCP y limita la JVM con `-XX:MaxRAMPercentage=75`.
6. CI: `.github/workflows/ci.yml` corre `mvnw verify`, lint + build del frontend y la validación del arnés en cada push a `develop`/`main` y en cada PR.
