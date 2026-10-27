# 🛒 POS System — Punto de Venta, Inventario y Facturación Electrónica

Plataforma SaaS **multi-local** de Punto de Venta (POS) para comercios minoristas: ventas en caja, control de inventario en tiempo real y facturación electrónica (DIAN Colombia). Cada comercio (`store`) opera aislado de los demás, y un equipo de plataforma (`SUPER_ADMIN`) administra locales, verificación KYC y credenciales de facturación desde un back office. El repositorio es un **monorepo** con dos proyectos independientes:

* **`backend/`** — API REST en **Java 21 + Spring Boot**, bajo un enfoque de **Monolito Modular** (Vertical Slicing).
* **`frontend/`** — SPA en **React 19 + Vite + Tailwind CSS 4**, con interfaz "Bento" y soporte de tema claro/oscuro.

La documentación de negocio y arquitectura (en español) vive en [`backend/docs/`](./backend/docs/_index.md) — es la fuente de verdad ante cualquier duda.

---

## 📋 Tabla de Contenidos
- [Estructura del Repositorio](#-estructura-del-repositorio)
- [Características Principales](#-características-principales)
- [Arquitectura del Backend](#-arquitectura-del-backend)
- [Stack Tecnológico](#-stack-tecnológico)
- [Módulos del Backend](#-módulos-del-backend)
- [Páginas del Frontend](#-páginas-del-frontend)
- [Roles y Permisos](#-roles-y-permisos)
- [Requisitos Previos](#-requisitos-previos)
- [Puesta en Marcha — Backend](#-puesta-en-marcha--backend)
- [Puesta en Marcha — Frontend](#-puesta-en-marcha--frontend)
- [Documentación](#-documentación)
- [Variables de Entorno](#-variables-de-entorno)
- [Pruebas Automatizadas](#-pruebas-automatizadas)

---

## 📂 Estructura del Repositorio

```
POS/
├── backend/                  # API REST — Java 21 / Spring Boot
│   ├── src/main/java/com/sciencebot/pos/   # Módulos verticales (auth, sales, inventory, billing, ...)
│   ├── src/main/resources/db/migration/    # Migraciones Flyway (V1 … V17)
│   ├── src/test/java/...                   # Pruebas unitarias e integración
│   ├── docs/                               # Documentación (arquitectura, convenciones, specs por módulo, roadmap)
│   ├── .env.example                        # Plantilla de variables de entorno
│   ├── docker-compose.yml                  # Entorno de desarrollo (PostgreSQL + app)
│   ├── docker-compose.prod.yml             # Entorno de producción
│   ├── Dockerfile                          # Multi-stage: dev / builder / prod
│   └── mvnw / mvnw.cmd                     # Maven Wrapper
│
├── frontend/                 # SPA — React 19 / Vite / Tailwind CSS 4
│   ├── src/pages/            # Páginas del local, `backoffice/` (plataforma) y `onboarding/`
│   ├── src/components/       # Layout (Header, Sidebar), backoffice, POS, clientes, auth
│   ├── src/context/          # AuthContext, ThemeContext, ModalContext
│   ├── src/services/         # Clientes HTTP por módulo (http.js es el cliente compartido)
│   └── vite.config.js        # Proxy de /api → http://localhost:8080
│
├── .ai/                      # Arnés de agentes de IA (protocolo Leader/Implementer/Reviewer/Committer)
├── POS_Completo.postman_collection.json              # Colección de Postman — API completa
├── POS_Flujo_Registro_Facturacion.postman_collection.json  # Flujo registro → habilitación → facturación
└── DESIGN.md                 # Lineamientos de diseño de la interfaz
```

---

## ✨ Características Principales

* ⚡ **Procesamiento Ágil de Ventas**: Registro de ventas en mostrador con soporte para lector de código de barras y cálculo automático de cambio.
* 📦 **Control de Inventario en Tiempo Real**: Deducción automática de stock con un `UPDATE` atómico y condicionado (la fila queda bloqueada hasta el commit y nunca queda negativa), sin pérdida de actualizaciones ni sobreventa con ventas concurrentes.
* 🏷️ **Generación de Códigos de Barras (Code 128)**: Visualización e impresión de la etiqueta de código de barras de cada producto directamente desde el catálogo y el inventario.
* 🏢 **Multi-local (SaaS)**: aislamiento de datos por local (`store_id` en el JWT → `TenantContext`), auto-registro del dueño con verificación de correo, onboarding con documentos KYC y back office para la plataforma.
* 🧾 **Facturación Electrónica DIAN por local**: integración con **Factus API** mediante adaptador desacoplado; las credenciales de cada local se guardan **cifradas (AES-256-GCM)** en base de datos, con ciclo de habilitación, gestión de rangos de numeración y auditoría. La emisión es asíncrona (evento `SaleCompletedEvent`) y nunca bloquea la venta.
* ↩️ **Devoluciones, métodos de pago y subcategorías**: devoluciones de venta, catálogo dinámico de métodos de pago y subcategorías propias de cada local.
* 🔔 **Notificaciones**: alertas por local (stock bajo, estado de facturación, onboarding).
* ☁️ **Archivos en Cloudflare R2**: documentos KYC y logo del negocio.
* 📊 **Reportes y Analítica con CQRS**: Consultas analíticas optimizadas de solo lectura (`Read Model`) implementadas con **`JdbcClient`**, eliminando sobrecostos de ORM y problemas N+1.
* 🚚 **Gestión de Abastecimiento**: Registro de compras a proveedores con incremento automático de existencias.
* 🔐 **Seguridad y Trazabilidad**: JWT de corta vida + refresh token rotativo con detección de reuso, cookies `HttpOnly`, control de acceso por roles (RBAC) y auditoría de transacciones.
* 🌗 **Interfaz con Tema Claro / Oscuro**: Panel administrativo tipo "Bento" totalmente responsivo, con preferencia de tema persistida por usuario.

---

## 🏛 Arquitectura del Backend

El backend sigue una arquitectura de **Monolito Modular**:

```
                  ┌────────────────────────────────────────────────┐
                  │          API REST / Controladores JSON         │
                  └───────┬────────────────────────────────┬───────┘
                          │                                │
                          ▼                                ▼
              ┌──────────────────────┐          ┌──────────────────────┐
              │    MÓDULO: sales     │          │  MÓDULO: inventory   │
              │  (Vertical Slice)    │          │  (Vertical Slice)    │
              │                      │          │                      │
              │  + API Pública       │          │  + API Pública       │
              │  + Lógica Interna    │          │  + Lógica Interna    │
              └───────────┬──────────┘          └───────────┬──────────┘
                          │                                │
                          └────────────────┬───────────────┘
                                           │ JPA / JdbcClient
                                           ▼
                  ┌────────────────────────────────────────────────┐
                  │           PostgreSQL 16 (o H2 en `dev`)        │
                  └────────────────────────────────────────────────┘
```

* **API Pública del Módulo (`Facade` / DTOs)**: Interfaces de entrada expuestas para interactuar entre dominios.
* **Lógica Interna (`internal/`)**: Entidades JPA, repositorios y servicios encapsulados (`package-private`), inaccesibles desde otros módulos.
* **Multi-tenencia**: `JwtAuthenticationFilter` guarda el `storeId` del token en `TenantContext` durante la petición; las consultas por local lo leen de ahí. `categories` es el único catálogo global.
* **Eventos de dominio**: flujos secundarios (p. ej. emitir la factura DIAN tras una venta) se desacoplan con eventos in-process.
* **Read Models (CQRS)**: Para el módulo de reportes (`reports`), se utiliza `JdbcClient` con SQL nativo y agregaciones en base de datos (`SUM`, `COUNT`, `GROUP BY`), desacoplado de las entidades de dominio.

El frontend es un **consumidor puro de la API**: no contiene lógica de negocio, solo llama a los endpoints REST (`frontend/src/services/http.js` y los `*Api.js`) y mantiene la sesión con las cookies `HttpOnly` que emite `/api/v1/auth/login`.

---

## 🛠 Stack Tecnológico

### Backend
* **Lenguaje**: Java 21 LTS
* **Framework**: Spring Boot 4.1 (Spring WebMvc, Spring Data JPA, Spring Security, Spring JDBC, Bean Validation, Flyway)
* **Base de Datos**: PostgreSQL 16 (Docker / producción) y H2 **en archivo** `backend/data/app_db` (perfil `dev`, por defecto)
* **Seguridad**: JWT HMAC con refresh token rotativo en cookies `HttpOnly`
* **Almacenamiento**: Cloudflare R2 (API S3)
* **Facturación**: Factus API (Estándar DIAN) / Mock Adapter
* **Almacenamiento**: Cloudflare R2 (SDK S3)
* **Documentación**: SpringDoc OpenAPI 3 / Swagger UI
* **Contenedores**: Docker & Docker Compose
* **Gestor de Dependencias**: Apache Maven (Wrapper incluido)

### Frontend
* **Librería**: React 19 + React Router 7
* **Build Tool**: Vite 8 (requiere Node 20.19+ o 22.12+)
* **Estilos**: Tailwind CSS 4 (modo oscuro basado en clase, tema persistido en `localStorage`)
* **Iconografía**: lucide-react
* **Códigos de Barras**: JsBarcode (renderizado Code 128 en el navegador)
* **Linter**: oxlint

---

## 🧩 Módulos del Backend

| Módulo | Paquete | Descripción |
|---|---|---|
| **Auth** | `com.sciencebot.pos.auth` | Login, logout, refresh rotativo, auto-registro y verificación de correo. |
| **Users** | `com.sciencebot.pos.users` | Gestión de usuarios, perfiles y estados de actividad. |
| **Categories** | `com.sciencebot.pos.categories` | Clasificación y categorías del catálogo de productos. |
| **Products** | `com.sciencebot.pos.products` | Catálogo de productos, códigos internos, códigos de barras y precios. |
| **Inventory** | `com.sciencebot.pos.inventory` | Ajustes de stock, entradas/salidas y control de mínimos. |
| **Customers** | `com.sciencebot.pos.customers` | Base de datos de clientes y datos fiscales para facturación. |
| **Suppliers** | `com.sciencebot.pos.suppliers` | Directorio de proveedores para compras. |
| **Purchases** | `com.sciencebot.pos.purchases` | Registro de compras de abastecimiento que aumentan el stock. |
| **Sales** | `com.sciencebot.pos.sales` | Procesamiento de ventas en efectivo y generación de comprobantes. |
| **Billing** | `com.sciencebot.pos.billing` | Facturas, notas crédito y débito electrónicas (DIAN vía Factus), habilitación y rangos por local. |
| **Reports** | `com.sciencebot.pos.reports` | Analítica de ventas, productos más vendidos, bajo stock y rentabilidad (CQRS / `JdbcClient`). |
| **Settings** | `com.sciencebot.pos.settings` | Configuración del negocio (nombre, NIT, logo) y catálogos dinámicos de parámetros. |
| **Stores** | `com.sciencebot.pos.stores` | Locales (tenants): auto-registro, onboarding y verificación de documentos KYC. |
| **Legal** | `com.sciencebot.pos.legal` | Textos legales de la plataforma (privacidad, términos). |
| **Support** | `com.sciencebot.pos.support` | PQRs y reportes de bugs con seguimiento público por radicado. |
| **Notifications** | `com.sciencebot.pos.notifications` | Bandeja de avisos por local (stock bajo, documentos, facturación). |
| **Storage** | `com.sciencebot.pos.storage` | Subida y borrado de archivos en Cloudflare R2. |
| **Backoffice** | `com.sciencebot.pos.backoffice` | Operadores de plataforma (`SUPER_ADMIN`) y métricas del embudo de registro. |

La especificación de cada módulo está en [`backend/docs/modules/`](./backend/docs/modules/). Estado, deuda técnica y plan de trabajo: [`backend/docs/_roadmap.md`](./backend/docs/_roadmap.md).

---

## 🖥️ Páginas del Frontend

### Públicas
| Ruta | Descripción |
|---|---|
| `/login` | Inicio de sesión. |
| `/register` | Auto-registro del dueño del comercio. |
| `/verify-email` | Verificación del correo desde el enlace recibido. |
| `/onboarding/store` | Registro del local y carga de documentos KYC (mientras el local está pendiente de revisión se muestra una pantalla de espera). |

### Local (`ADMINISTRATOR`, `SUPERVISOR`, `SELLER`)
| Ruta | Página | Descripción |
|---|---|---|
| `/` | Dashboard | KPIs de ventas, últimos 7 días, productos bajo mínimo, facturas DIAN y notificaciones. |
| `/pos` | Punto de Venta | Carrito, escaneo de códigos de barras, cobro con métodos de pago y emisión de factura DIAN. |
| `/products` | Productos | Catálogo, subcategorías y etiquetas de código de barras. |
| `/inventory` | Inventario | Kardex, compras, proveedores (`?tab=suppliers`) y devoluciones (`?tab=returns`). |
| `/customers` | Clientes | Directorio para facturación electrónica. |
| `/invoicing` | Facturación DIAN | Facturas emitidas, habilitación y rangos de numeración del local. |
| `/sales` | Ventas | Historial de ventas con filtros por fecha, búsqueda y detalle. |
| `/cash` | Caja | Arqueo (esperado vs contado), efectivo por cajero y recaudo por método de pago (solo `ADMINISTRATOR`). |

Las analíticas (ventas por vendedor, top de productos, rentabilidad, compras, estado de inventario) viven en el Dashboard (`/`); `/reports` redirige allí.
| `/documents` | Documentos | Documentos KYC del local. |
| `/settings` | Configuración | Datos del negocio, logo y parámetros. |

### Back office (`SUPER_ADMIN`, bajo `/backoffice`)
| Ruta | Descripción |
|---|---|
| `/backoffice` | Locales y métricas SaaS. |
| `/backoffice/onboarding` | Cola y estadísticas de onboarding. |
| `/backoffice/billing` | Habilitación de facturación electrónica y credenciales por local. |
| `/backoffice/categories`, `/product-categories` | Categorías de comercio y categorías globales de producto. |
| `/backoffice/parameters` | Catálogos dinámicos de parámetros. |
| `/backoffice/legal-documents` | Textos legales. |
| `/backoffice/support` | Tickets de soporte (PQRs/bugs). |
| `/backoffice/staff` | Personal de plataforma. |

Todas las páginas respetan el tema claro/oscuro seleccionado desde el encabezado (`ThemeContext`, persistido en `localStorage`).

---

## 👥 Roles y Permisos

| Rol | Perfil | Alcance |
|---|---|---|
| `SUPER_ADMIN` | Equipo de la plataforma | Back office (`/api/v1/backoffice/**`): locales y KYC, credenciales de facturación, categorías globales, textos legales, soporte y personal. No pertenece a ningún local. |
| `ADMINISTRATOR` | Dueño de un local | Control total de **su** local: usuarios, reportes, facturación, rangos de numeración y configuración. |
| `SUPERVISOR` | Supervisor de Tienda | Compras, inventario, clientes y reintentos de facturas. |
| `SELLER` | Vendedor / Cajero | Operación de caja: ventas, consulta de catálogo y tickets. |

Todos los roles excepto `SUPER_ADMIN` están acotados a su propio local. Matriz completa en [`backend/docs/_security.md`](./backend/docs/_security.md).

---

## 📦 Requisitos Previos

* **Java 21 JDK** (si ejecutas el backend de forma nativa).
* **Node.js 20.19+ (o 22.12+)** y **npm** (para el frontend).
* **Docker** y **Docker Compose** (opcional, para PostgreSQL y despliegue contenedorizado).
* **Git**.

---

## 🚀 Puesta en Marcha — Backend

Todos los comandos se ejecutan desde la carpeta `backend/`.

### Opción 1: Con Docker Compose (Recomendado)

Levanta PostgreSQL y la aplicación en contenedores:

```bash
cd backend

# Levantar los contenedores de desarrollo
docker compose up -d

# Para verificar los logs de la aplicación
docker compose logs -f app
```

La aplicación quedará disponible en: `http://localhost:8080`
Base de datos PostgreSQL expuesta en el puerto: `5434` (usuario: `postgres`, contraseña: `postgres`, bd: `app_db`; solo para desarrollo).

### Opción 2: Ejecución Local con Maven

Por defecto el perfil activo es `dev`, que usa una base de datos **H2 en archivo** (`backend/data/app_db`, persiste entre reinicios; bórrala para empezar de cero) — no necesitas Docker ni PostgreSQL para levantar el backend localmente:

* **Linux / macOS**:
  ```bash
  cd backend
  ./mvnw spring-boot:run
  ```
* **Windows (PowerShell / CMD)**:
  ```powershell
  cd backend
  .\mvnw.cmd spring-boot:run
  ```

Si prefieres usar PostgreSQL en vez de H2, levanta solo la base de datos con Docker y cambia el perfil activo:

```bash
cd backend
docker compose up -d db
```

Usuarios de desarrollo (solo perfil `dev`): `admin` / `Password123` (local 1) y `superadmin` (clave en `SEED_SUPERADMIN_PASSWORD`, o `SuperAdmin123!` si no la defines). **Fuera de `dev` no se siembra ningún `admin`**: los locales se crean por el auto-registro y el `SUPER_ADMIN` solo se crea si defines `SEED_SUPERADMIN_PASSWORD`.

> Copia `backend/.env.example` a `backend/.env` y ajústalo (se carga automáticamente con `mvnw spring-boot:run`). En `dev` solo hace falta definirlo si quieres cambiar los valores por defecto.

---

## 🚀 Puesta en Marcha — Frontend

El frontend consume la API en `http://localhost:8080` mediante el proxy configurado en `vite.config.js` (asegúrate de tener el backend corriendo primero).

```bash
cd frontend
npm install
npm run dev
```

La aplicación quedará disponible en: `http://localhost:3000`

Otros scripts disponibles:

```bash
npm run build     # Build de producción
npm run preview   # Previsualizar el build de producción
npm run lint      # Linter (oxlint)
```

---

## 📖 Documentación

* **Documentación del proyecto** (arquitectura, convenciones, seguridad, esquema, specs por módulo y roadmap): 📁 [`backend/docs/_index.md`](./backend/docs/_index.md)
* **Swagger UI interactivo** (con el backend en ejecución): 👉 [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* **OpenAPI Docs (JSON)**: 👉 [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
* **Colecciones de Postman** (importar en Postman):
  * [`POS_Completo.postman_collection.json`](./POS_Completo.postman_collection.json) — API completa.
  * [`POS_Flujo_Registro_Facturacion.postman_collection.json`](./POS_Flujo_Registro_Facturacion.postman_collection.json) — flujo de registro, habilitación y facturación.
  * Aún no cubren notas crédito/débito, storage ni gran parte del back office.

---

## ⚙️ Variables de Entorno

Puedes personalizar la configuración del backend mediante variables de entorno en tu sistema o en el archivo `.env` (dentro de `backend/`):

| Variable | Valor por Defecto | Descripción |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Perfil activo (`dev` usa H2 en archivo, consola H2 y un secreto JWT público; **en producción usa `prod`**). |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/app_db` | URL de conexión a la base de datos PostgreSQL. |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Usuario de la base de datos. |
| `SPRING_DATASOURCE_PASSWORD` | *(vacío)* | Contraseña de la base de datos. |
| `JWT_SECRET` | *(solo `dev` tiene un valor por defecto)* | Clave secreta (≥ 32 bytes) para firmar tokens JWT. **Obligatoria fuera de `dev`.** |
| `JWT_REFRESH_REUSE_GRACE_MS` | `15000` | Ventana de gracia ante reuso del refresh token. |
| `JWT_COOKIE_SECURE` | `false` | Atributo `Secure` de las cookies de sesión. **Ponlo en `true` en producción (HTTPS).** |
| `APP_FRONTEND_URL` | `http://localhost:3000` | URL pública del SPA: enlaces de los correos y origen CORS permitido. |
| `APP_CORS_ALLOWED_ORIGINS` | *(= `APP_FRONTEND_URL`)* | Orígenes CORS permitidos, separados por coma. |
| `EMAIL_PROVIDER` | `log` | `log` solo escribe el correo en el log; usa `smtp` en producción. |
| `EMAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | *(vacío)* | Envío por SMTP (verificación de correo y alertas). |
| `SEED_SUPERADMIN_PASSWORD` | *(vacío; `dev` tiene un valor)* | Contraseña del `SUPER_ADMIN` inicial. Sin ella no se siembra la cuenta. |
| `JWT_EXPIRATION_MS` | `900000` (15 minutos) | Tiempo de vida del access token en milisegundos. |
| `JWT_REFRESH_EXPIRATION_MS` | `604800000` (7 días) | Tiempo de vida del refresh token en milisegundos. |
| `BILLING_PROVIDER` | `factus` | Proveedor de facturación (`factus` o `mock`). Un valor desconocido hace fallar la facturación (no cae a `mock`). |
| `BILLING_ENCRYPTION_KEY` | *(vacío)* | Clave AES-256 (32 bytes en Base64) que cifra en la base de datos las credenciales de Factus de cada local. **Sin ella no se pueden guardar credenciales**; si se pierde o cambia, los secretos ya guardados dejan de poder leerse. |
| `FACTUS_URL_SANDBOX` | `https://api-sandbox.factus.com.co` | URL del entorno de pruebas de Factus. |
| `FACTUS_URL_PRODUCTION` | `https://api.factus.com.co` | URL del entorno de producción de Factus. |
| `R2_ACCOUNT_ID` | *(vacío)* | ID de cuenta de Cloudflare (define el endpoint `https://<id>.r2.cloudflarestorage.com`). |
| `R2_ACCESS_KEY_ID` | *(vacío)* | Access Key ID del token API de Cloudflare R2. |
| `R2_SECRET_ACCESS_KEY` | *(vacío)* | Secret Access Key del token API de Cloudflare R2. |
| `R2_BUCKET_NAME` | `pos-storage` | Nombre del bucket de R2. Dentro conviven las carpetas `documentos`, `imagenes-productos` e `imagenes-logos`. |
| `R2_PUBLIC_URL` | *(vacío)* | **Obligatoria para subir archivos.** URL pública del bucket (Public Development URL o dominio propio conectado en R2) con la que se arma la URL de cada archivo. Si falta (o conserva el valor de ejemplo `pub-example.r2.dev`), la subida falla con un error claro en vez de guardar un enlace roto. |

> **Credenciales de Factus**: no se configuran por variable de entorno. El `SUPER_ADMIN` las carga **por local**
> desde `PUT /api/v1/backoffice/stores/{storeId}/billing/credentials` y quedan cifradas en la tabla
> `store_billing_config`. Un local sin credenciales propias no factura: no hay respaldo a una cuenta global.
> Ver `backend/docs/modules/billing/spec.md`.

> Sin las 5 variables `R2_*`, `POST /api/v1/storage/upload` sigue respondiendo pero falla al intentar conectarse a R2 (no hay fallback silencioso). Corriendo con `mvnw spring-boot:run`, `backend/.env` se carga automáticamente (la propia aplicación lo importa con `spring.config.import`); con Docker Compose, `docker-compose.yml`/`docker-compose.prod.yml` ya las reenvían al contenedor.

El frontend no requiere variables de entorno propias: las llamadas a `/api` se redirigen al backend mediante el proxy de Vite (`frontend/vite.config.js`).

---

## 🧪 Pruebas Automatizadas

Desde la carpeta `backend/`:

```bash
# Linux / macOS
./mvnw test

# Windows
.\mvnw.cmd test
```

Para generar el empaquetado de producción (`JAR`):

```bash
# Linux / macOS
./mvnw clean package -DskipTests

# Windows
.\mvnw.cmd clean package -DskipTests
```
