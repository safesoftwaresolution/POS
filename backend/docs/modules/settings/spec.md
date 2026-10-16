<!-- spec-version: 1.2 | last-updated: 2026-10-16 -->
# Módulo: Configuración (Settings)

## Dependencias
- **Requiere**: storage (logo), billing (`CompanyLogoServiceImpl` usa `FactusTokenManager` de `billing.internal`: **violación de encapsulación** pendiente de exponer vía `BillingFacade`, ver `_roadmap.md`)
- **Requerido por**: users (`DatabaseSeeder` siembra los ajustes del local de desarrollo), sales/billing (datos del negocio en facturas y tickets)

## Reglas de Negocio

### RN-SET-001 — Una Configuración por Local
- Existe **una fila por local**: la PK de `settings` es el `id` del local (`stores.id`), asignada
  explícitamente desde `TenantContext`, no autogenerada (desde `V9__Store_Scoping_Isolation.sql`).
- Un local recién registrado todavía no tiene fila; la primera consulta pretende crearla con valores por defecto
  editables, en vez de responder 404. **A verificar**: `getSettings` es `readOnly` y la creación se invoca por auto-invocación, así que
  el `@Transactional` interno no se aplica y la fila podría no persistirse hasta el primer `PUT`.

### RN-SET-002 — Sin Credenciales de Facturación
- Este módulo **no guarda credenciales de Factus**. Las columnas `factus_*` que existieron aquí se
  eliminaron en `V16__Drop_Settings_Factus_Columns.sql`; la habilitación y las credenciales por local viven
  en `store_billing_config`, propiedad del módulo `billing` (ver `modules/billing/spec.md`).

## Requerimientos Funcionales

### RF-SET-001 — Configurar Datos del Negocio
- **Prioridad**: Alta
- Campos: businessName, address, phone, taxId, email, logoUrl.
- Solo lectura/escritura, no se crea ni elimina (registro único).

### RF-SET-002 — Consultar Configuración
- **Prioridad**: Alta
- Retorna la configuración actual del negocio.

### RN-SET-003 — Catálogos Dinámicos de Parámetros
- `parameter_topics` / `parameter_values` (`V6`) guardan listas editables (`PAYMENT_METHODS` y otras). Los temas con `is_system = true` son los que el sistema usa por código (no se crean desde la API). Solo `SUPER_ADMIN` crea temas y valores; todos los roles los leen. Un valor se desactiva (`active = false`) en vez de borrarse.

### RN-SET-004 — Logo del Negocio
- `POST /api/v1/settings/logo` (alias `/company/logo` y `/companies/logo`, solo `ADMINISTRATOR`): PNG/JPG de hasta 200 KB y 300×300 px. Se sube a `storage` (carpeta `logos`) y se sincroniza con Factus usando el token del local; si Factus no está habilitado para el local, el logo se guarda sin sincronizar.

## API Endpoints

### GET /api/v1/settings
- **Permisos**: Admin, Supervisor
- **Response 200**:
  ```json
  { "businessName": "Mi Tienda", "address": "Calle Principal 123", "phone": "555-0001", "taxId": "900111222-3", "email": "info@mitienda.com", "logoUrl": null }
  ```

### PUT /api/v1/settings
- **Permisos**: Admin
- **Request**: Mismo esquema del GET
- **Response 200**: Configuración actualizada

### Catálogos (`/api/v1/settings/parameters`)
- `GET /topics` y `GET /topics/{code}/values`: todos los roles (incluido `SUPER_ADMIN`).
- `POST /topics`, `POST /topics/{code}/values`, `PUT /values/{id}`, `DELETE /values/{id}`: solo `SUPER_ADMIN`.

### Logo
- `POST /api/v1/settings/logo` (multipart `file`): `ADMINISTRATOR`.

## Modelo de Datos

### Tabla: `settings`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK = `stores.id` del local dueño (1 a 1, no autogenerada) |
| business_name | VARCHAR(100) | NOT NULL |
| address | VARCHAR(255) | NOT NULL |
| phone | VARCHAR(20) | NOT NULL |
| tax_id | VARCHAR(50) | NULLABLE |
| email | VARCHAR(100) | NULLABLE |
| logo_url | VARCHAR(255) | NULLABLE |
| updated_at | TIMESTAMP | NOT NULL |

## Permisos
| Operación | Admin | Supervisor | Vendedor |
|---|---|---|---|
| Leer | ✓ | ✓ | - |
| Editar | ✓ | - | - |
