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
| `SWAGGER_ENABLED` | Enciende `/v3/api-docs` y Swagger UI en `prod` (apagado por defecto) | `false` |
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
5. La imagen `prod` fija la zona horaria `America/Bogota` (`TZ` y `-Duser.timezone`), trae un `HEALTHCHECK` TCP y limita la JVM con `-XX:MaxRAMPercentage=75` (ver «Capacidad y límites»).
6. CI: `.github/workflows/ci.yml` corre `mvnw verify`, lint + build del frontend y la validación del arnés en cada push a `develop`/`main` y en cada PR.

## Capacidad y límites

Referencia: VPS de ~2 vCPU / 8 GB / 100 GB (Dokploy). Orden de riesgo al crecer: *disco → pool de conexiones/reportes → CPU en picos y deploys → RAM*. Las cifras de clientes son **estimaciones no medidas** hasta correr `backend/loadtest/` (ver su README).

| Estimación (sin medir) | Lectura |
|---|---|
| ~100 locales | cómodo |
| 100-200 locales | amarillo: vigilar pool y CPU |
| > 200 locales | rediseñar (Postgres aparte, réplicas) |

### Configuración aplicada (perfil `prod` y `docker-compose.prod.yml`)
- **Hikari**: `maximum-pool-size=20` (`HIKARI_MAX_POOL_SIZE`), `connection-timeout=10000`, `leak-detection-threshold` apagado (`HIKARI_LEAK_DETECTION_MS` solo para depurar). Postgres trae `max_connections=100` (fijado explícitamente en el compose): cubre pool × réplicas hasta ~4 réplicas.
- **Timeout de consultas**: `spring.jdbc.template.query-timeout=30`. `JdbcClient` lo hereda porque se crea sobre el `JdbcTemplate` autoconfigurado, así que cubre los reportes (`ReportDaoJdbc`) y las consultas de back-office.
- **`spring.jpa.open-in-view=false`** (todos los perfiles): la conexión se libera al cerrar la transacción, no al terminar la respuesta HTTP.
- **Memoria**: backend `mem_limit: 1536m` (heap ≈ 1,1 GB con `MaxRAMPercentage=75`); Postgres `mem_limit: 1g`. Si Dokploy no usa el compose, fijar lo mismo en *Advanced → Resources*.
- **Postgres**: `shared_buffers=256MB`, `effective_cache_size=2GB`, `work_mem=8MB` (conservadores para 8 GB compartidos).

### Disco
- Dokploy → Settings → Web Server: activar *Daily Docker Cleanup*.
- Una vez por SSH: `docker builder prune -f && docker image prune -f`; luego revisar *Monitoring → Docker Disk Usage* (objetivo: Build Cache < 5 GB).
- Registrar el tamaño del volumen de Postgres como línea base de crecimiento.

### Backups de Postgres
1. Dokploy → servicio Postgres → *Backups*: dump diario a un bucket R2/S3 **distinto** del de imágenes, retención 14-30 días.
2. Restauración (probar al menos una vez en una BD temporal, nunca sobre producción):
   ```bash
   createdb -U postgres app_restore_test
   pg_restore -U postgres -d app_restore_test --no-owner backup.dump   # o: psql ... < backup.sql si es SQL plano
   psql -U postgres -d app_restore_test -c "SELECT count(*) FROM sales;"
   dropdb -U postgres app_restore_test
   ```
3. Anotar aquí la fecha de la última restauración probada: _pendiente_.

### Imagen del backend construida en CI
El job `backend-image` de `ci.yml` (solo push a `main`, tras pasar backend/frontend/harness) construye el target `prod` y lo publica en `ghcr.io/<owner>/<repo>-backend` con tags `latest` y `<sha>`, con caché de capas `gha`. En Dokploy, cambiar el origen del backend de *Git build* a *Docker image* (GHCR, con credenciales de lectura de paquetes) y redeploy por webhook tras la publicación. El VPS deja de compilar Maven.

### Reglas operativas
- No desplegar en picos: quincena (15/30), viernes y 5-7 pm.
- Alertas sugeridas (Dokploy notifications / uptime externo): disco > 70 %, RAM > 75 % sostenida, CPU > 70 % durante 10 min, healthcheck caído.
- Subir a 4 vCPU / 16 GB si: p95 de `POST /api/v1/sales` > 800 ms en pico, conexiones pendientes del pool > 0 de forma recurrente, o CPU > 70 % sostenida. Pasados ~100-150 locales: Postgres en servidor propio y evaluar segunda réplica del backend (JWT sin estado) y réplica de lectura para reportes.
- Pendiente opcional: `spring-boot-starter-actuator` (solo health + métricas Hikari, protegido) para ver el pool; hoy el healthcheck es TCP.

## Distribución de dominios (producción)

| Dominio | Qué sirve | App en Dokploy |
|---|---|---|
| `posbento.com` y `www.posbento.com` | Landing pública estática (`landing/`) | `landing` (Build Path `/landing`, Nixpacks, Publish Directory `dist`) |
| `app.posbento.com` | El sistema (SPA de `frontend/`) | `frontend` |
| `api.posbento.com` | API REST (`backend/`) | `backend` |

Los tres registros DNS son de tipo A hacia la IP del VPS, en modo **solo DNS** (sin proxy de Cloudflare), para que Traefik emita y renueve los certificados Let's Encrypt.

Variables que dependen de esta distribución:

| Variable | Dónde | Valor |
|---|---|---|
| `APP_FRONTEND_URL` | backend | `https://app.posbento.com` (los correos de verificación y recuperación enlazan aquí) |
| `APP_CORS_ALLOWED_ORIGINS` | backend | `https://app.posbento.com`. Durante la migración, `https://posbento.com,https://app.posbento.com` |
| `PUBLIC_APP_URL` | landing (se incrusta al compilar; cambiarla exige redesplegar) | `https://app.posbento.com` (valor por defecto) |

Las cookies de sesión son `SameSite=Strict`: `app.` y `api.` comparten el sitio registrable `posbento.com`, por lo que siguen enviándose.

### Migración desde `posbento.com` como sistema
Antes de la landing, el sistema vivía en `posbento.com`; los correos ya enviados y los favoritos apuntan a `/verify-email?token=…`, `/reset-password?token=…` y `/login`. La landing las reenvía a `app.posbento.com` conservando el token (`landing/src/pages/`).

1. DNS: registro A de `app` (y `www` solo DNS).
2. App `frontend`: agregar `app.posbento.com` con HTTPS y conservar `posbento.com`. Backend: `APP_CORS_ALLOWED_ORIGINS` con ambos orígenes, y reiniciar. Probar login, refresco de sesión y una venta.
3. App `landing`: crearla con un dominio temporal y `PUBLIC_APP_URL=https://app.posbento.com`; revisar botones y reenvíos.
4. Corte (horario de poco movimiento): `APP_FRONTEND_URL=https://app.posbento.com` y reiniciar el backend; quitar `posbento.com` y `www` del `frontend`; agregarlos a `landing` y quitar el dominio temporal. La interrupción dura lo que tarda Traefik en emitir el certificado.
5. Limpieza: dejar `APP_CORS_ALLOWED_ORIGINS` solo con `https://app.posbento.com`; probar un correo real de verificación y uno de recuperación.

Reversa: devolver `posbento.com` al `frontend` y restaurar el valor anterior de `APP_FRONTEND_URL`.
