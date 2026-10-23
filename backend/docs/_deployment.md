<!-- doc-version: 1.2 | last-updated: 2026-10-02 -->
# Despliegue y Configuración

## Requisitos del Servidor
| Componente | Mínimo | Recomendado |
|---|---|---|
| SO | Linux Ubuntu 22.04 / Windows Server 2022 | Ubuntu 22.04 LTS |
| JDK | 21 LTS | Amazon Corretto 21 |
| CPU/RAM | 1 vCPU / 2 GB | 2 vCPU / 4 GB |
| Disco | 10 GB | 20 GB |
| BD | PostgreSQL 16 | PostgreSQL 16, 5 GB inicial |

## Variables de Entorno
Fuente de verdad: `backend/src/main/resources/application.properties` y `backend/.env.example`. En ejecución local, `backend/.env` se carga automáticamente (`spring.config.import`).

| Variable | Descripción | Por defecto / ejemplo |
|---|---|---|
| SPRING_PROFILES_ACTIVE | Perfil activo (`dev` = H2 en archivo `./data/app_db`; otro perfil = PostgreSQL) | `dev` |
| SPRING_DATASOURCE_URL / _USERNAME / _PASSWORD | Conexión a la base de datos | PostgreSQL `localhost:5432/app_db` |
| DB_NAME / DB_USER / DB_PASSWORD | Solo para Docker Compose | (secreto) |
| JWT_SECRET | Clave simétrica JWT (≥256 bits). Obligatoria fuera de `dev` | (secreto) |
| JWT_EXPIRATION_MS / JWT_REFRESH_EXPIRATION_MS | TTL access (15 min) / refresh (7 días) | 900000 / 604800000 |
| JWT_REFRESH_REUSE_GRACE_MS | Ventana de gracia ante reuso de refresh por carreras de red | 15000 |
| JWT_COOKIE_NAME / JWT_REFRESH_COOKIE_NAME / JWT_COOKIE_SECURE / JWT_COOKIE_SAME_SITE | Cookies de sesión (`Secure=true` en producción con HTTPS) | `jwt_token` / `refresh_token` / false / Strict |
| EMAIL_VERIFICATION_EXPIRATION_MS | Vigencia del enlace de verificación de correo | 86400000 |
| EMAIL_PROVIDER / EMAIL_FROM / SMTP_HOST / SMTP_PORT / SMTP_USERNAME / SMTP_PASSWORD | Envío de correo (`log` solo escribe en el log) | `log` |
| APP_FRONTEND_URL | URL del frontend para armar enlaces de correo | `http://localhost:3000` |
| BILLING_PROVIDER | `factus` o `mock` | `factus` |
| BILLING_ENCRYPTION_KEY | Clave AES-256 (32 bytes Base64) que cifra las credenciales Factus por local. No perderla ni cambiarla | (secreto) |
| FACTUS_URL_SANDBOX / FACTUS_URL_PRODUCTION | URLs públicas de Factus (las credenciales NO van en variables: viven cifradas en BD por local) | URLs oficiales |
| R2_ACCOUNT_ID / R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY / R2_BUCKET_NAME / R2_PUBLIC_URL | Cloudflare R2 (módulo `storage`) | vacío / `pos-storage` |

> **Atención — `docker-compose.prod.yml`**: hoy solo reenvía al contenedor `DB_*`, `JWT_SECRET`, `BILLING_ENCRYPTION_KEY`, `FACTUS_URL_*` y `R2_*`. **No reenvía** `BILLING_PROVIDER`, `EMAIL_PROVIDER`/`EMAIL_FROM`/`SMTP_*`, `APP_FRONTEND_URL` ni `JWT_COOKIE_SECURE`/`JWT_COOKIE_SAME_SITE`, así que en ese compose se usan los valores por defecto (correo en modo `log`, cookies sin `Secure`). Ver `_roadmap.md`.

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
- Esquema: gestionado exclusivamente por **Flyway** (`db/migration/V1…V20`); `ddl-auto=validate` en `dev` y `prod`. Solo el perfil `test` deja que Hibernate cree el esquema.
- Datos semilla (`DatabaseSeeder` y otros seeders): `superadmin` / `SuperAdmin123!` (SUPER_ADMIN), `admin` / `Password123` (ADMINISTRATOR del local 1), "Cliente General" (RN-CUST-001), categorías base. **Cambiar estas credenciales en cualquier entorno expuesto.** `ProductDemoSeeder` solo corre en `dev`.

---

## Despliegue Containerizado (Docker y Docker Compose)

El proyecto cuenta con soporte nativo para despliegue en contenedores Docker mediante una construcción multi-etapa en el `Dockerfile`.

### 1. Entorno de Desarrollo (Local)
Utiliza la etapa `dev` del Dockerfile. Monta el código fuente y el repositorio local de Maven como volúmenes para acelerar compilaciones y habilitar el reinicio en caliente.

**Comandos**:
- Levantar servicios (PowerShell): `.\docker-dev.ps1 start`
- Levantar servicios (Unix Bash): `./docker-dev.sh start`
- Detener servicios: `docker compose down`

### 2. Entorno de Producción
Utiliza la etapa `prod` del Dockerfile. Construye el JAR internamente y genera una imagen JRE ligera y segura sin código fuente ni dependencias de desarrollo, ejecutando bajo el usuario no privilegiado `spring`.

**Comandos**:
- Levantar servicios en producción:
  ```bash
  docker compose -f docker-compose.prod.yml up --build -d
  ```
- Detener servicios en producción:
  ```bash
  docker compose -f docker-compose.prod.yml down
  ```

### Transición de Desarrollo a Producción

El paso de desarrollo a producción se realiza de manera limpia:
1. **Sin dependencias del host**: Todo el proceso de empaquetado final ocurre dentro del contenedor de construcción de Docker (etapa `builder`), garantizando que no se arrastren archivos locales innecesarios ni configuraciones corruptas del host.
2. **Configuración de Variables de Entorno**: En producción, asegúrate de proporcionar los valores correctos para las variables de entorno de base de datos (`DB_NAME`, `DB_USER`, `DB_PASSWORD`), seguridad (`JWT_SECRET`, `JWT_COOKIE_SECURE=true`), facturación (`BILLING_ENCRYPTION_KEY`) y `R2_*`, las cuales sobrescriben los valores por defecto configurados en `application.properties`.
3. **Esquema**: nunca lo modifica Hibernate (`ddl-auto=validate` siempre); todo cambio entra como una nueva migración Flyway.

