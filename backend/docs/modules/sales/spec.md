<!-- spec-version: 1.2 | last-updated: 2026-10-02 -->
# Módulo: Ventas (Sales)

## Dependencias
- **Requiere**: products, inventory, customers, users, billing, settings (catálogo `PAYMENT_METHODS`), stores (tenancy)
- **Requerido por**: reports

## Reglas de Negocio

### RN-SALE-001 — Método de Pago
- El método de pago es uno de los códigos activos del catálogo dinámico `PAYMENT_METHODS` (`settings`), ej. `CASH`, `NEQUI`, `CARD`, `TRANSFER`. Por defecto `CASH`. Ya no está restringido solo a efectivo.

### RN-SALE-002 — Venta Original Inmutable; Devoluciones como Registro Aparte
- Una venta confirmada nunca se edita ni se elimina (sigue siendo el comprobante fiscal inmutable). Las devoluciones (`sale_returns`) son registros independientes ligados a la venta y a los `sale_items` puntuales devueltos: reponen stock y calculan el monto a reembolsar sin tocar la venta original. Ver RF-SALE-005.

### RN-SALE-003 — Aislamiento por Tienda
- Toda venta pertenece a una tienda (`store_id`, vía `TenantContext`). Un usuario solo ve y crea ventas de su propia tienda.

### RN-INV-001 (ref: inventory/spec.md)
- No se puede vender más cantidad de la disponible en stock.

## Requerimientos Funcionales

### RF-SALE-001 — Crear Venta (Punto de Venta)
- **Prioridad**: Alta (Bloqueante)
- **Criterios de Aceptación**:
  - Buscar productos por nombre, código interno o código de barras.
  - Cliente opcional (por defecto: "Cliente General").
  - Carrito: agregar/quitar productos, ajustar cantidad.
  - Validar stock disponible para cada ítem (RN-INV-001).
  - Cálculo en tiempo real: `Subtotal = Σ(Cantidad × Precio_Venta)`, `Total = Subtotal`.

### RF-SALE-002 — Registrar Venta (Efectivo, Cambio y Emisión Electrónica)
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Campo obligatorio: cashReceived ≥ totalAmount.
  - Cálculo: `cashChange = cashReceived - totalAmount`.
  - Al confirmar:
    - Venta guardada con estado COMPLETADA.
    - Stock reducido atómicamente para cada ítem.
    - Movimiento de inventario tipo VENTA registrado.
    - Número de factura correlativo generado (ej. FACT-000001).
    - Si `sendToFactus: true`, la venta publica `SaleCompletedEvent`; `billing` lo escucha tras el commit (`AFTER_COMMIT`, `@Async`) y emite la factura electrónica DIAN sin bloquear al cajero (ver RN-BILL-002b).

### RF-SALE-003 — Generar Factura y Ticket POS
- **Prioridad**: Alta
- Contenido obligatorio: datos del negocio, nº factura, fecha/hora, cliente, ítems, total, recibido, cambio.
- Formato optimizado para ticketera térmica 58mm/80mm y enlace a factura electrónica si fue emitida.

### RF-SALE-004 — Consultar Historial de Ventas
- **Prioridad**: Alta
- Listado: nº factura, fecha, cliente, total, vendedor.
- Detalle expandible: factura original.
- Filtros: rango de fechas, cliente, vendedor.

### RF-SALE-005 — Devolución de Venta
- **Prioridad**: Media
- Se selecciona una venta y una o más líneas (`sale_items`) puntuales a devolver, con cantidad ≤ lo vendido en esa línea menos lo ya devuelto previamente de esa misma línea.
- Repone stock del/los producto(s) devuelto(s) (movimiento de inventario) y calcula `total_refund`.
- La venta original no se modifica; la devolución queda como registro propio (`sale_returns` / `sale_return_items`) con motivo (`reason`) opcional.

## Requerimientos No Funcionales

### RNF-SALE-001 — Rendimiento POS
- Búsqueda y agregación de producto por barcode < 100ms.

## API Endpoints

### POST /api/v1/sales
- **Permisos**: Admin, Supervisor, Vendedor
- **Request**:
  ```json
  {
    "customerId": 1, "cashReceived": 20.00,
    "items": [{ "productId": 1, "quantity": 2 }, { "productId": 2, "quantity": 5 }]
  }
  ```
- **Response 201**:
  ```json
  {
    "id": 124, "invoiceNumber": "FACT-000124", "createdAt": "2026-07-07T10:48:00Z",
    "customerName": "Cliente General", "totalAmount": 8.10,
    "cashReceived": 20.00, "cashChange": 11.90, "sellerUsername": "vendedor1",
    "items": [{ "productName": "Coca Cola 350ml", "quantity": 2, "unitPrice": 1.80, "subtotal": 3.60 }]
  }
  ```
- **Errores**: 400 (efectivo insuficiente), 409 (stock insuficiente)

### GET /api/v1/sales
- **Permisos**: Admin, Supervisor, Vendedor
- **Query params**: dateFrom, dateTo, customerId, userId, page, size

### GET /api/v1/sales/{id}
- **Permisos**: Admin, Supervisor, Vendedor
- Detalle completo con ítems

## Modelo de Datos

### Tabla: `sales`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| invoice_number | VARCHAR(50) | UNIQUE, NOT NULL |
| store_id | BIGINT | FK → stores(id), NOT NULL |
| customer_id | BIGINT | FK → customers(id), NOT NULL |
| payment_method | VARCHAR(30) | NOT NULL, DEFAULT 'CASH' |
| total_amount | DECIMAL(12,2) | NOT NULL |
| cash_received | DECIMAL(12,2) | NOT NULL |
| cash_change | DECIMAL(12,2) | NOT NULL |
| user_id | BIGINT | FK → users(id), NOT NULL |
| created_at | TIMESTAMP | NOT NULL |

**Constraints**: FK_sales_customer, FK_sales_user, FK_sales_store, CK_sale_payment: `cash_received >= total_amount`

### Tabla: `sale_returns`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| sale_id | BIGINT | FK → sales(id), NOT NULL |
| store_id | BIGINT | FK → stores(id), NOT NULL |
| user_id | BIGINT | FK → users(id), NOT NULL |
| reason | VARCHAR(255) | NULLABLE |
| total_refund | DECIMAL(12,2) | NOT NULL |
| created_at | TIMESTAMP | NOT NULL |

### Tabla: `sale_return_items`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| sale_return_id | BIGINT | FK → sale_returns(id) ON DELETE CASCADE, NOT NULL |
| sale_item_id | BIGINT | FK → sale_items(id), NOT NULL |
| product_id | BIGINT | FK → products(id), NOT NULL (copia de sale_items.product_id al momento de la devolución) |
| quantity | INT | NOT NULL |
| unit_price | DECIMAL(12,2) | NOT NULL |
| subtotal | DECIMAL(12,2) | NOT NULL |

### Tabla: `sale_items`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| sale_id | BIGINT | FK → sales(id) ON DELETE CASCADE, NOT NULL |
| product_id | BIGINT | FK → products(id), NOT NULL |
| quantity | INT | NOT NULL |
| unit_price | DECIMAL(12,2) | NOT NULL |
| subtotal | DECIMAL(12,2) | NOT NULL |

## Permisos
| Operación | Admin | Supervisor | Vendedor |
|---|---|---|---|
| Crear | ✓ | ✓ | ✓ |
| Leer | ✓ | ✓ | ✓ |
| Editar | - | - | - |
| Eliminar | - | - | - |
