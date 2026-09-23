# 🛒 POS System — Punto de Venta, Inventario y Facturación Electrónica

Sistema integral de Punto de Venta (POS) para comercios minoristas: ventas en caja, control de inventario en tiempo real y facturación electrónica (DIAN Colombia). El repositorio es un **monorepo** con dos proyectos independientes:

* **`backend/`** — API REST en **Java 21 + Spring Boot**, bajo un enfoque de **Monolito Modular** (Vertical Slicing).
* **`frontend/`** — SPA en **React 19 + Vite + Tailwind CSS 4**, con interfaz "Bento" y soporte de tema claro/oscuro.

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
- [Documentación de la API](#-documentación-de-la-api)
- [Variables de Entorno](#-variables-de-entorno)
- [Pruebas Automatizadas](#-pruebas-automatizadas)

---

## 📂 Estructura del Repositorio

```
POS/
├── backend/                  # API REST — Java 21 / Spring Boot
│   ├── src/main/java/com/sciencebot/pos/   # Módulos verticales (auth, sales, inventory, ...)
│   ├── src/test/java/...                   # Pruebas unitarias e integración
│   ├── docker-compose.yml                  # Entorno de desarrollo (PostgreSQL + app)
│   ├── docker-compose.prod.yml             # Entorno de producción
│   ├── Dockerfile                          # Multi-stage: dev / builder / prod
│   ├── mvnw / mvnw.cmd                     # Maven Wrapper
│   └── POS_System_API_Postman_Collection.json
│
└── frontend/                 # SPA — React 19 / Vite / Tailwind CSS 4
    ├── src/pages/            # Dashboard, POS, Inventario, Productos, Clientes, Facturación, Configuración
    ├── src/components/       # Layout (Header, Sidebar) y componentes reutilizables (ej. BarcodeModal)
    ├── src/context/          # AuthContext, ThemeContext (tema claro/oscuro)
    ├── src/services/api.js   # Cliente HTTP hacia la API del backend
    └── vite.config.js        # Proxy de /api → http://localhost:8080
```

---

## ✨ Características Principales

* ⚡ **Procesamiento Ágil de Ventas**: Registro de ventas en mostrador con soporte para lector de código de barras y cálculo automático de cambio.
* 📦 **Control de Inventario en Tiempo Real**: Deducción automática de stock con bloqueo pesimista (`Pessimistic Locking`) para prevenir condiciones de carrera concurrentes.
* 🏷️ **Generación de Códigos de Barras (Code 128)**: Visualización e impresión de la etiqueta de código de barras de cada producto directamente desde el catálogo y el inventario.
* 🧾 **Facturación Electrónica DIAN**: Integración con **Factus API** y adaptador desacoplado para emisión de facturas electrónicas colombianas.
* 📊 **Reportes y Analítica con CQRS**: Consultas analíticas optimizadas de solo lectura (`Read Model`) implementadas con **`JdbcClient`**, eliminando sobrecostos de ORM y problemas N+1.
* 🚚 **Gestión de Abastecimiento**: Registro de compras a proveedores con incremento automático de existencias.
* 🔐 **Seguridad y Trazabilidad**: Autenticación mediante **JWT**, control de acceso basado en roles (RBAC) y auditoría de transacciones.
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
* **Read Models (CQRS)**: Para el módulo de reportes (`reports`), se utiliza `JdbcClient` con SQL nativo y agregaciones en base de datos (`SUM`, `COUNT`, `GROUP BY`), desacoplado de las entidades de dominio.

El frontend es un **consumidor puro de la API**: no contiene lógica de negocio, solo llama a los endpoints REST (`frontend/src/services/api.js`) y mantiene la sesión mediante el token JWT devuelto por `/api/v1/auth/login`.

---

## 🛠 Stack Tecnológico

### Backend
* **Lenguaje**: Java 21 LTS
* **Framework**: Spring Boot (Spring WebMvc, Spring Data JPA, Spring Security, Spring JDBC)
* **Base de Datos**: PostgreSQL 16 (Docker / producción) y H2 en memoria (perfil `dev`, por defecto)
* **Seguridad**: JWT (HMAC-SHA256)
* **Facturación**: Factus API (Estándar DIAN) / Mock Adapter
* **Documentación**: SpringDoc OpenAPI 3 / Swagger UI
* **Contenedores**: Docker & Docker Compose
* **Gestor de Dependencias**: Apache Maven (Wrapper incluido)

### Frontend
* **Librería**: React 19 + React Router 7
* **Build Tool**: Vite
* **Estilos**: Tailwind CSS 4 (modo oscuro basado en clase, tema persistido en `localStorage`)
* **Iconografía**: lucide-react
* **Códigos de Barras**: JsBarcode (renderizado Code 128 en el navegador)
* **Linter**: oxlint

---

## 🧩 Módulos del Backend

| Módulo | Paquete | Descripción |
|---|---|---|
| **Auth** | `com.sciencebot.pos.auth` | Autenticación, login, logout y validación de tokens JWT. |
| **Users** | `com.sciencebot.pos.users` | Gestión de usuarios, perfiles y estados de actividad. |
| **Categories** | `com.sciencebot.pos.categories` | Clasificación y categorías del catálogo de productos. |
| **Products** | `com.sciencebot.pos.products` | Catálogo de productos, códigos internos, códigos de barras y precios. |
| **Inventory** | `com.sciencebot.pos.inventory` | Ajustes de stock, entradas/salidas y control de mínimos. |
| **Customers** | `com.sciencebot.pos.customers` | Base de datos de clientes y datos fiscales para facturación. |
| **Suppliers** | `com.sciencebot.pos.suppliers` | Directorio de proveedores para compras. |
| **Purchases** | `com.sciencebot.pos.purchases` | Registro de compras de abastecimiento que aumentan el stock. |
| **Sales** | `com.sciencebot.pos.sales` | Procesamiento de ventas en efectivo y generación de comprobantes. |
| **Billing** | `com.sciencebot.pos.billing` | Emisión y sincronización de facturación electrónica con la DIAN. |
| **Reports** | `com.sciencebot.pos.reports` | Analítica de ventas, productos más vendidos, bajo stock y rentabilidad (CQRS / `JdbcClient`). |
| **Settings** | `com.sciencebot.pos.settings` | Configuración del negocio (nombre, NIT, encabezados de ticket). |

---

## 🖥️ Páginas del Frontend

| Ruta | Página | Descripción |
|---|---|---|
| `/login` | Login | Autenticación contra la API, sesión persistida vía JWT. |
| `/` | Dashboard | KPIs de ventas, gráfico de últimos 7 días, productos bajo mínimo y facturas DIAN. |
| `/pos` | Punto de Venta | Carrito de venta, búsqueda de productos, cobro en efectivo y emisión de factura DIAN. |
| `/products` | Catálogo de Productos | Alta, baja y consulta de productos; visualización e impresión de código de barras. |
| `/inventory` | Inventario | Kardex de stock, registro de compras a proveedor, código de barras por producto. |
| `/customers` | Clientes | Directorio de clientes para facturación electrónica. |
| `/invoicing` | Facturación DIAN | Monitoreo de facturas electrónicas emitidas vía Factus. |
| `/settings` | Configuración | Datos del negocio y parámetros de facturación electrónica. |

Todas las páginas respetan el tema claro/oscuro seleccionado desde el encabezado (`ThemeContext`, persistido en `localStorage`).

---

## 👥 Roles y Permisos

| Rol | Perfil | Alcance |
|---|---|---|
| `ADMIN` | Administrador / Dueño | Control total: gestión de usuarios, reportes, facturación y configuración. |
| `SUPERVISOR` | Supervisor de Tienda | Compras a proveedores, inventario, clientes y reintentos de facturas. |
| `SELLER` | Vendedor / Cajero | Operación de caja: ventas en efectivo, consulta de catálogo y tickets. |

---

## 📦 Requisitos Previos

* **Java 21 JDK** (si ejecutas el backend de forma nativa).
* **Node.js 18+** y **npm** (para el frontend).
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
Base de datos PostgreSQL expuesta en el puerto: `5434` (usuario: `postgres`, contraseña: `postgres`, bd: `app_db`).

### Opción 2: Ejecución Local con Maven

Por defecto el perfil activo es `dev`, que usa una base de datos **H2 en memoria** — no necesitas Docker ni PostgreSQL para levantar el backend localmente:

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

Usuario administrador por defecto para iniciar sesión: `admin` / `Password123`.

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

## 📖 Documentación de la API

Con el backend en ejecución:

* **Swagger UI interactivo**:
  👉 [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

* **OpenAPI Docs (JSON)**:
  👉 [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

* **Colección de Postman**:
  El repositorio incluye la colección lista para importar en:
  📁 [`backend/POS_System_API_Postman_Collection.json`](./backend/POS_System_API_Postman_Collection.json)

---

## ⚙️ Variables de Entorno

Puedes personalizar la configuración del backend mediante variables de entorno en tu sistema o en el archivo `.env` (dentro de `backend/`):

| Variable | Valor por Defecto | Descripción |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Perfil activo (`dev` usa H2 en memoria; para PostgreSQL usa otro perfil junto con Docker). |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/app_db` | URL de conexión a la base de datos PostgreSQL. |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Usuario de la base de datos. |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Contraseña de la base de datos. |
| `JWT_SECRET` | *Clave de 256 bits por defecto* | Clave secreta para firmar tokens JWT. |
| `JWT_EXPIRATION_MS` | `900000` (15 minutos) | Tiempo de vida del access token en milisegundos. |
| `JWT_REFRESH_EXPIRATION_MS` | `604800000` (7 días) | Tiempo de vida del refresh token en milisegundos. |
| `BILLING_PROVIDER` | `factus` | Proveedor de facturación (`factus` o `mock`). |
| `BILLING_ENCRYPTION_KEY` | *(vacío)* | Clave AES-256 (32 bytes en Base64) que cifra en la base de datos las credenciales de Factus de cada local. **Sin ella no se pueden guardar credenciales**; si se pierde o cambia, los secretos ya guardados dejan de poder leerse. |
| `FACTUS_URL_SANDBOX` | `https://api-sandbox.factus.com.co` | URL del entorno de pruebas de Factus. |
| `FACTUS_URL_PRODUCTION` | `https://api.factus.com.co` | URL del entorno de producción de Factus. |
| `R2_ACCOUNT_ID` | *(vacío)* | ID de cuenta de Cloudflare (define el endpoint `https://<id>.r2.cloudflarestorage.com`). |
| `R2_ACCESS_KEY_ID` | *(vacío)* | Access Key ID del token API de Cloudflare R2. |
| `R2_SECRET_ACCESS_KEY` | *(vacío)* | Secret Access Key del token API de Cloudflare R2. |
| `R2_BUCKET_NAME` | `pos-storage` | Nombre del bucket de R2. Dentro conviven las carpetas `documentos`, `imagenes-productos` e `imagenes-logos`. |
| `R2_PUBLIC_URL` | `https://pub-example.r2.dev` | URL pública del bucket (Public Development URL o dominio propio conectado en R2) usada para armar la URL de cada archivo subido. |

> **Credenciales de Factus**: no se configuran por variable de entorno. El `SUPER_ADMIN` las carga **por local**
> desde `PUT /api/v1/backoffice/stores/{storeId}/billing/credentials` y quedan cifradas en la tabla
> `store_billing_config`. Un local sin credenciales propias no factura: no hay respaldo a una cuenta global.
> Ver `backend/docs/modules/billing/spec.md`.

> Sin las 5 variables `R2_*`, `POST /api/v1/storage/upload` sigue respondiendo pero falla al intentar conectarse a R2 (no hay fallback silencioso). Corriendo con `mvnw spring-boot:run`, `backend/.env` se carga automáticamente (vía `spring-dotenv`); con Docker Compose, `docker-compose.yml`/`docker-compose.prod.yml` ya las reenvían al contenedor.

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
