<!-- spec-version: 1.4 | last-updated: 2026-10-29 -->
# Módulo: Ventas (Sales)

## Dependencias
- **Requiere**: products, services, inventory, customers, users, stores (tenancy)
- **Requerido por**: reports, billing (billing consume `SaleFacade` y escucha `SaleCompletedEvent`)

> `sales` **no** importa `billing` ni `settings`: la emisión electrónica se desacopla por el evento `SaleCompletedEvent` (ver RF-SALE-002).

## Reglas de Negocio

### RN-SALE-001 — Método de Pago
- El método de pago es uno de los códigos activos del catálogo dinámico `PAYMENT_METHODS` (`settings`), ej. `CASH`, `NEQUI`, `CARD`, `TRANSFER`. Por defecto `CASH`. Ya no está restringido solo a efectivo.
- **Limitación actual**: el código solo normaliza el texto (trim y mayúsculas, máx. 30 caracteres); **no** lo valida contra el catálogo, porque `sales` no depende de `settings`. Pendiente: validar vía `SettingsFacade`.

### RN-SALE-002 — Venta Original Inmutable; Devoluciones como Registro Aparte
- Una venta confirmada nunca se edita ni se elimina (sigue siendo el comprobante fiscal inmutable). Las devoluciones (`sale_returns`) son registros independientes ligados a la venta y a los `sale_items` puntuales devueltos: reponen stock y calculan el monto a reembolsar sin tocar la venta original. Ver RF-SALE-005.

### RN-SALE-003 — Aislamiento por Tienda
- Toda venta pertenece a una tienda (`store_id`, vía `TenantContext`). Un usuario solo ve y crea ventas de su propia tienda.

### RN-SALE-004 — Líneas de Producto o de Servicio
- Cada línea de venta es **un producto o un servicio**, nunca ambos ni ninguno (`CreateSaleItemCommand`: exactamente uno entre `productId` y `serviceId`; si no, 400). La BD lo garantiza con `CK_sale_items_target` (`item_type`).
- Una línea de **servicio** no valida existencias, no registra movimiento de inventario y exige que el servicio exista en la tienda (404 si no) y esté activo (400 si no). Ver `services/spec.md`.
- Cada línea guarda una **copia de lo facturado**: nombre (`item_name`), precio, IVA, y para servicios también `unit_measure_code` y `unspsc_code`. Editar o desactivar el servicio después no cambia la venta emitida. Las líneas anteriores a `V29` no tienen copia de nombre y se resuelven desde el producto.
- Una **devolución** de una línea de servicio solo reembolsa: no hay stock que reponer.

### RN-SALE-005 — Un Vendedor Solo Ve sus Ventas
- Un usuario con rol `SELLER` solo consulta las ventas que **él registró** (`sales.user_id`): `GET /sales` las filtra en la consulta, y `GET /sales/{id}`, `GET /sales/{id}/returns` y `POST /sales/{id}/returns` responden 404 si la venta es de otro vendedor. `ADMINISTRATOR` y `SUPERVISOR` ven las de todo el local.
- Lo decide el backend por el rol de la sesión (`SaleFacade.restrictedSellerId()`), no la interfaz. Las facturas electrónicas siguen la misma regla (`billing/spec.md`, RN-BILL-020).

### RN-INV-001 (ref: inventory/spec.md)
- No se puede vender más cantidad de la disponible en stock (aplica a líneas de producto). La reducción de stock es un `UPDATE` atómico y condicionado (`ProductFacade.adjustStock`, ver `inventory/spec.md`): dos ventas concurrentes del mismo producto se serializan y no pueden sobrevender.

## Requerimientos Funcionales

### RF-SALE-001 — Crear Venta (Punto de Venta)
- **Prioridad**: Alta (Bloqueante)
- **Criterios de Aceptación**:
  - Buscar productos por nombre, código interno o código de barras, y servicios por nombre o código (RN-SALE-004).
  - Cliente opcional (por defecto: "Cliente General").
  - Carrito: agregar/quitar productos, ajustar cantidad.
  - Validar stock disponible para cada ítem de producto (RN-INV-001); los servicios no tienen stock.
  - Cálculo en tiempo real: `Subtotal = Σ(Cantidad × Precio_Venta)`, `Total = Subtotal`.

### RF-SALE-002 — Registrar Venta (Efectivo, Cambio y Emisión Electrónica)
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Campo obligatorio: cashReceived ≥ totalAmount.
  - Cálculo: `cashChange = cashReceived - totalAmount`.
  - Al confirmar:
    - Venta guardada (no existe columna ni enum de estado: una venta confirmada es inmutable).
    - Stock reducido atómicamente para cada ítem.
    - Movimiento de inventario tipo VENTA registrado.
    - Número de factura correlativo generado (ej. FACT-000001).
    - Se publica `SaleCompletedEvent` (siempre). Si `sendToFactus: true`, `billing` lo escucha tras el commit (`@Async` + `AFTER_COMMIT`) y emite la factura electrónica DIAN; una falla de Factus no afecta a la venta.
  - Idempotencia: el header `Idempotency-Key` (opcional, máx. 80 caracteres) evita duplicar la venta ante reenvíos; la misma clave devuelve la venta original.

### RF-SALE-003 — Generar Factura y Ticket POS
- **Prioridad**: Alta
- Contenido obligatorio: datos del negocio, nº factura, fecha/hora, cliente, ítems, total, recibido, cambio.
- Formato optimizado para ticketera térmica 58mm/80mm y enlace a factura electrónica si fue emitida.

### RF-SALE-004 — Consultar Historial de Ventas
- **Prioridad**: Alta
- Listado: nº factura, fecha, cliente, total, vendedor.
- Detalle expandible: factura original.
- Filtros: rango de fechas y cliente (`dateFrom`, `dateTo`, `customerId`). El filtro por vendedor (`userId`) **no está implementado**.

### RF-SALE-005 — Devolución de Venta
- **Prioridad**: Media
- Se selecciona una venta y una o más líneas (`sale_items`) puntuales a devolver, con cantidad ≤ lo vendido en esa línea menos lo ya devuelto previamente de esa misma línea.
- Repone stock del/los producto(s) devuelto(s) (movimiento de inventario) y calcula `total_refund`. Las líneas de servicio solo reembolsan, sin tocar inventario (RN-SALE-004).
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
    "customerId": 1, "cashReceived": 20.00, "paymentMethod": "CASH", "sendToFactus": false,
    "items": [{ "productId": 1, "quantity": 2 }, { "serviceId": 4, "quantity": 1 }]
  }
  ```
  Cada ítem lleva `productId` **o** `serviceId`. En la respuesta, cada ítem incluye `itemType` (`PRODUCT`/`SERVICE`), `serviceId`, `unitMeasureCode` y `unspscCode` (los dos últimos solo en servicios).
- **Response 201**:
  ```json
  {
    "id": 124, "invoiceNumber": "FACT-000124", "createdAt": "2026-07-07T10:48:00Z",
    "customerName": "Cliente General", "totalAmount": 8.10,
    "cashReceived": 20.00, "cashChange": 11.90, "sellerUsername": "vendedor1",
    "items": [{ "productName": "Coca Cola 350ml", "quantity": 2, "unitPrice": 1.80, "subtotal": 3.60 }]
  }
  ```
- **Headers opcionales**: `Idempotency-Key`.
- **Validación**: `items` no vacío y cada `quantity` > 0 (`@Valid` → 400).
- **Errores**: 400 (campos inválidos, efectivo insuficiente, **stock insuficiente**: hoy responde 400 porque el servicio lanza `IllegalArgumentException`; este spec decía 409 y el código no lo implementa), 404 (producto o cliente inexistente en el local)

### GET /api/v1/sales
- **Permisos**: Admin, Supervisor, Vendedor
- **Query params**: dateFrom, dateTo, customerId, page, size (sin tope máximo de `size`)

### GET /api/v1/sales/{id}
- **Permisos**: Admin, Supervisor, Vendedor
- Detalle completo con ítems

### POST /api/v1/sales/{id}/returns
- **Permisos**: Admin, Supervisor, Vendedor
- **Request**: `CreateSaleReturnCommand { reason, items: [{ saleItemId, quantity }] }` (RF-SALE-005).
- **Errores**: 400 (cantidad mayor a lo disponible por devolver), 404 (venta o línea inexistente).

### GET /api/v1/sales/{id}/returns
- **Permisos**: Admin, Supervisor, Vendedor. Lista las devoluciones de la venta.

## Modelo de Datos

### Tabla: `sales`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| invoice_number | VARCHAR(50) | UNIQUE, NOT NULL |
| idempotency_key | VARCHAR(80) | NULLABLE, UNIQUE global (`UK_sales_idempotency_key`, `V5`) |
| store_id | BIGINT | FK → stores(id), NOT NULL |
| customer_id | BIGINT | FK → customers(id), NOT NULL |
| payment_method | VARCHAR(30) | NOT NULL, DEFAULT 'CASH' |
| total_amount | DECIMAL(12,2) | NOT NULL |
| cash_received | DECIMAL(12,2) | NOT NULL |
| cash_change | DECIMAL(12,2) | NOT NULL |
| user_id | BIGINT | FK → users(id), NOT NULL |
| created_at | TIMESTAMP | NOT NULL |

**Constraints**: FK_sales_customer, FK_sales_user, FK_sales_store, CK_sale_payment: `cash_received >= total_amount` (se exige también con tarjeta o transferencia).

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
| product_id | BIGINT | FK → products(id), NULLABLE desde `V29` (copia de sale_items.product_id; null si la línea era un servicio) |
| service_id | BIGINT | FK → services(id), NULLABLE (`V29`; copia de sale_items.service_id) |
| quantity | INT | NOT NULL |
| unit_price | DECIMAL(12,2) | NOT NULL |
| subtotal | DECIMAL(12,2) | NOT NULL |

### Tabla: `sale_items`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| sale_id | BIGINT | FK → sales(id) ON DELETE CASCADE, NOT NULL |
| product_id | BIGINT | FK → products(id), NULLABLE desde `V29` (null si la línea es un servicio) |
| item_type | VARCHAR(10) | NOT NULL, DEFAULT 'PRODUCT' (`V29`): `PRODUCT` o `SERVICE` |
| service_id | BIGINT | FK → services(id), NULLABLE (`V29`) |
| item_name | VARCHAR(150) | NULLABLE (`V29`): copia del nombre facturado |
| unit_measure_code | VARCHAR(10) | NULLABLE (`V29`): unidad DIAN con que se facturó |
| unspsc_code | VARCHAR(50) | NULLABLE (`V29`): UNSPSC con que se facturó |
| quantity | INT | NOT NULL |
| unit_price | DECIMAL(12,2) | NOT NULL |
| subtotal | DECIMAL(12,2) | NOT NULL |
| tax_rate | DECIMAL(5,2) | NOT NULL, DEFAULT 0.00 (`V13`) |
| tax_amount | DECIMAL(12,2) | NOT NULL, DEFAULT 0.00 (`V13`) |

**Constraints** (`V29`): `CK_sale_items_target`: `(item_type = 'PRODUCT' AND product_id IS NOT NULL AND service_id IS NULL) OR (item_type = 'SERVICE' AND service_id IS NOT NULL AND product_id IS NULL)`; `FK_sale_items_service`. **Índice**: `IDX_sale_items_service`.

## Permisos
| Operación | Admin | Supervisor | Vendedor |
|---|---|---|---|
| Crear | ✓ | ✓ | ✓ |
| Leer | ✓ | ✓ | ✓ |
| Editar | - | - | - |
| Eliminar | - | - | - |
