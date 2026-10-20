<!-- spec-version: 1.1 | last-updated: 2026-09-22 -->
# Módulo: Configuración (Settings)

## Dependencias
- **Requiere**: ninguno
- **Requerido por**: sales (datos de factura: nombre, dirección, teléfono del negocio)

## Reglas de Negocio

### RN-SET-001 — Una Configuración por Local
- Existe **una fila por local**: la PK de `settings` es el `id` del local (`stores.id`), asignada
  explícitamente desde `TenantContext`, no autogenerada (desde `V9__Store_Scoping_Isolation.sql`).
- Un local recién registrado todavía no tiene fila; la primera consulta la crea con valores por defecto
  editables, en vez de responder 404.

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
