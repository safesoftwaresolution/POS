<!-- spec-version: 1.1 | last-updated: 2026-10-16 -->
# Módulo: Compras (Purchases)

## Dependencias
- **Requiere**: products, suppliers, inventory, users
- **Requerido por**: reports

## Reglas de Negocio

### RN-PUR-001 — Compras Irreversibles
- Una compra registrada no puede modificarse ni eliminarse bajo ninguna circunstancia.

### RN-PUR-002 — Actualización Automática de Precio de Compra
- Al registrar una compra, el `unit_cost` ingresado se convierte en el nuevo `purchase_price` del producto.
- **Excepción**: `ProductFacade.updatePurchasePrice` rechaza (400) un costo **mayor al precio de venta actual** del producto (para no vender con pérdida). Una compra legítima a un costo superior obliga a subir antes el precio de venta; la compra completa se revierte.
- **Idempotencia**: el header opcional `Idempotency-Key` (`purchases.idempotency_key`, `V5`) evita registrar dos veces la misma compra; la restricción `UNIQUE(supplier_id, invoice_number)` rechaza una factura de proveedor repetida con 409.

### RN-SUPP-001 (ref: suppliers/spec.md)
- Solo se asocian compras a proveedores con estado Activo.

## Requerimientos Funcionales

### RF-PUR-001 — Registrar Compra de Mercancía
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Campos: supplierId (proveedor activo), invoiceNumber (opcional), items[{productId, quantity, unitCost}].
  - Al confirmar:
    - Stock de cada producto sube atómicamente.
    - Se registra movimiento de inventario tipo COMPRA.
    - Se actualiza purchase_price del producto (RN-PUR-002).
    - Se calcula totalAmount = Σ(quantity × unitCost).

### RF-PUR-002 — Consultar Historial de Compras
- **Prioridad**: Alta
- Listado con: fecha, proveedor, total, usuario. Detalle expandible con ítems.
- Filtros: rango de fechas, supplierId.

## API Endpoints

### POST /api/v1/purchases
- **Permisos**: Admin, Supervisor
- **Request**:
  ```json
  {
    "supplierId": 1, "invoiceNumber": "FAC-PROV-001",
    "items": [{ "productId": 1, "quantity": 100, "unitCost": 1.15 }]
  }
  ```
- **Response 201**: Compra registrada con detalle
- **Errores**: 400 (proveedor inactivo, datos inválidos, costo mayor al precio de venta), 409 (factura de proveedor duplicada)

### GET /api/v1/purchases
- **Permisos**: Admin, Supervisor
- **Query params**: supplierId, dateFrom, dateTo, page, size

### GET /api/v1/purchases/{id}
- **Permisos**: Admin, Supervisor
- Detalle completo con ítems

## Modelo de Datos

### Tabla: `purchases`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| supplier_id | BIGINT | FK → suppliers(id), NOT NULL |
| invoice_number | VARCHAR(50) | NULLABLE |
| total_amount | DECIMAL(12,2) | NOT NULL |
| user_id | BIGINT | FK → users(id), NOT NULL |
| created_at | TIMESTAMP | NOT NULL |

### Tabla: `purchase_items`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| purchase_id | BIGINT | FK → purchases(id) ON DELETE CASCADE, NOT NULL |
| product_id | BIGINT | FK → products(id), NOT NULL |
| quantity | INT | NOT NULL |
| unit_cost | DECIMAL(12,2) | NOT NULL |
| subtotal | DECIMAL(12,2) | NOT NULL (`quantity × unit_cost`) |

## Permisos
| Operación | Admin | Supervisor | Vendedor |
|---|---|---|---|
| Crear | ✓ | ✓ | - |
| Leer | ✓ | ✓ | - |
| Editar | - | - | - |
| Eliminar | - | - | - |
