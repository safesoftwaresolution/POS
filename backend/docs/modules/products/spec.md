<!-- spec-version: 1.5 | last-updated: 2026-10-25 -->
# Módulo: Productos (Products)

## Dependencias
- **Requiere**: categories (categoría global obligatoria + subcategoría de tienda opcional, ver `modules/categories/spec.md`)
- **Requerido por**: inventory, purchases, sales

## Reglas de Negocio

### RN-PROD-001 — Margen de Utilidad Positivo
- `sale_price >= purchase_price` siempre. Enforced a nivel de BD con CHECK constraint.

### RN-PROD-002 — Código de Barras Único
- Cada barcode es único **por tienda** (nullable, pero si existe no puede repetirse dentro de la misma tienda; dos tiendas distintas sí pueden tener el mismo barcode). Igual para `internal_code`.

### RN-PROD-003 — Subcategoría Opcional y Consistente
- `subcategoryId` es opcional. Si se envía, debe pertenecer a la misma `categoryId` del producto y a la tienda actual (ver RN-SUBCAT-003 en `categories/spec.md`).

### RN-PROD-004 — Vencimiento por Lotes (Lotes Ligeros)
- El vencimiento es **opcional**: un producto sin lotes no vence y el sistema no le muestra ni le calcula nada.
- Cada **entrada** de stock (*Mover stock → Entrada*, *Compra* a proveedor, stock inicial al crear) puede traer un vencimiento y un código de lote opcional; eso guarda un **lote** (`product_lots`, `V27`): cantidad que entró + fecha + código. Solo las entradas (`ENTRY`/`ENTRADA`/`COMPRA`) aceptan fecha; en cualquier otro movimiento → 400.
- **No se descuenta stock por lote.** El stock del producto (`quantity_available`, con su `UPDATE` atómico) sigue siendo el único contador; ventas, devoluciones, conteos y concurrencia no cambian. Lo que queda de cada lote se **deriva** (RN-PROD-005).
- Fecha de un lote: pasada se acepta (mercancía ya vencida que hay que poder registrar); más de 20 años a futuro → 400 (casi seguro un error de digitación). Código de lote: máx. 50 caracteres.
- `ProductDto.expirationDate` es **derivado**: el vencimiento del lote más próximo que todavía tiene unidades (`null` si el producto no tiene lotes con unidades). No hay columna en `products`.
- Poner fecha a stock que ya existía sin ella: `POST /products/{id}/lots` crea un lote **sin mover stock** y exige `cantidad ≤ unidades sin fecha`. Corregir la fecha, el código o **las unidades** de un lote: `PUT /products/{id}/lots/{lotId}` (`quantity` opcional: sin ella las unidades no cambian). Quitar un lote: `DELETE` (el stock no cambia; las unidades que le quedaban pasan a "sin fecha"; dar de baja mercancía vencida es una salida por *Vencimiento*).
- **Cambiar las unidades de un lote** mueve unidades entre ese lote y las "sin fecha"; **nunca toca el stock del producto**: bajarlas las devuelve a "sin fecha"; subirlas las toma de "sin fecha" y falla con 400 si no alcanzan ("sube primero el stock con una entrada o un conteo"); 0 o negativo → 400 (para quitar el lote se elimina). Si el error también estaba en el stock, se corrige aparte con *Contar y ajustar*; el orden de ambos pasos no importa. Las unidades que se muestran y se editan son las que le **quedan** al lote (RN-PROD-005).
- `POST /products` acepta `expirationDate` como "vencimiento del stock inicial": exige `quantityAvailable > 0` y crea un lote con esas unidades (con stock 0 → 400).
- El listado de lotes vencidos y próximos a vencer es el reporte `GET /api/v1/reports/expiring` (ver `reports/spec.md`, RF-REP-007).

### RN-PROD-005 — Reparto FEFO del Stock entre Lotes
- Con stock `S` y lotes ordenados por (`expiration_date`, `id`) de cantidades `q1..qn`, `T = Σq`: lo consumido es `max(0, T − S)` y sale primero del lote que vence antes; `restante_i = qi − clamp(consumido − (q1+…+q(i−1)), 0, qi)`; las unidades **sin fecha** son `max(0, S − T)` (stock que entró sin lote).
- Es una **estimación** que asume que se vende primero lo que vence primero. Una venta, un conteo a la baja o una salida por *Vencimiento* bajan `S` y por eso consumen primero el lote más próximo.
- **Consolidación de lotes** (`ProductFacade.consolidateLots`, idempotente): como el reparto sale de "total de lotes − stock", un lote vendido hace tiempo seguiría sumando al total y **reaparecería** en cuanto el stock subiera sin lote o se quitara otro lote. Para evitarlo, antes de fijar el resultado se "congela" lo ya vendido: se **borran los lotes agotados** y a cada lote parcial se le deja en `quantity` solo lo que le queda. Se consolida (1) en `inventory`, antes de **cualquier movimiento que suba el stock** (entrada, compra, ajuste, devolución de cliente; las ventas y las salidas no consolidan, para no cargarlas), y (2) al asignar, corregir o quitar un lote. Por eso `quantity` es lo que le quedaba al lote en la última consolidación y no lo que entró originalmente.
- Consecuencias: una entrada sin fecha, un ajuste a la alza o una **devolución de cliente** dejan esas unidades **sin fecha** (no revive ningún lote); quitar un lote no revive lotes ya vendidos.
- Dos implementaciones de la misma regla de reparto: `ProductLotAllocator` (Java, lista de lotes y vencimiento del producto) y la consulta con funciones de ventana de `reports` (RF-REP-007). `ExpiringProductsReportTest.sqlAndJavaAllocationAgree` comprueba que repartan igual.
- **Las salidas manuales no permiten elegir el lote** (decisión 2026-10-25): *Mover stock → Salida* siempre descuenta del que vence primero, igual que una venta. Si se necesita sacar unidades de un lote concreto, el selector de lote en la salida quedó descartado por ahora.
- **Límites**: si se vende en otro orden, el reparto por lote puede diferir de la realidad; una entrada sin fecha en un producto que ya tiene lotes deja esas unidades sin fecha (por eso la pantalla pide la fecha en cada entrada de un producto que ya vence); la consolidación usa el stock que lee en ese instante, así que una venta concurrente justo entre la lectura y el movimiento puede mover unas pocas unidades entre lotes; la importación por Excel no crea lotes.

## Requerimientos Funcionales

### RF-PROD-001 — Crear Producto
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Campos obligatorios: internalCode (único por tienda), name, categoryId, purchasePrice, salePrice.
  - Campos opcionales: barcode (único por tienda si ingresado), subcategoryId (ver RN-PROD-003), description, imageUrl, expirationDate (vencimiento del stock inicial, ver RN-PROD-004).
  - Stock inicial: 0 (se alimenta vía módulo de Compras o Inventario).
  - Validar RN-PROD-001 (precio venta ≥ precio compra).
  - Un `barcode` en blanco se guarda como `NULL` (un `""` chocaría con la unicidad por tienda).
  - Carga masiva: la importación desde Excel (crear y actualizar productos en bloque, con conteo físico) vive en `inventory` (RF-INV-005) y usa esta misma fachada; `ProductFacade.findByInternalCodes` / `findByBarcodes` resuelven los códigos del archivo en lote.

### RF-PROD-002 — Editar Producto
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Editable: name, description, categoryId, purchasePrice, salePrice, barcode, minStock, imageUrl. El vencimiento no se edita aquí: se gestiona por lotes (`/products/{id}/lots`, RN-PROD-004).
  - Validar RN-PROD-001 al cambiar precios.
  - No editar directamente quantityAvailable (se modifica vía Inventario/Compras/Ventas).

### RF-PROD-003 — Consultar y Buscar Productos
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Búsqueda por: barcode, internalCode, name.
  - Filtro por: categoryId, active.
  - Paginación: `page` (default 0), `size` (default 20).

### RF-PROD-004 — Activar/Desactivar Producto
- **Prioridad**: Alta
- Producto inactivo no aparece en el POS de ventas.

### RF-PROD-005 — Eliminar Producto (Borrado Lógico)
- **Prioridad**: Media
- Borrado lógico si tiene transacciones históricas.

## API Endpoints

### GET /api/v1/products
- **Permisos**: Administrator, Supervisor, Seller (acotado a la tienda actual vía `TenantContext`)
- **Query params**: search, categoryId, page, size
- **Response 200**:
  ```json
  {
    "content": [{ "id": 1, "internalCode": "PROD-001", "barcode": "7701234567890", "name": "Coca Cola 350ml", "categoryName": "Bebidas", "purchasePrice": 1.20, "salePrice": 1.80, "quantityAvailable": 45, "minStock": 10, "active": true }],
    "totalPages": 1, "totalElements": 1
  }
  ```

### POST /api/v1/products
- **Permisos**: Administrator, Supervisor
- **Request**:
  ```json
  { "internalCode": "PROD-002", "barcode": "7701112223334", "name": "Papas Fritas", "categoryId": 2, "subcategoryId": 7, "purchasePrice": 0.50, "salePrice": 0.90, "minStock": 20, "expirationDate": "2027-03-01" }
  ```
  `expirationDate` (ISO `yyyy-MM-dd`) es opcional y exige `quantityAvailable > 0` (crea un lote); la respuesta y el listado devuelven siempre `expirationDate` (derivado de los lotes, `null` si no vence).
- **Response 201**: Objeto producto creado
- **Errores**: 400 (código duplicado, RN-PROD-001 violado, subcategoría inconsistente con la categoría, fecha de vencimiento a más de 20 años o sin stock inicial)

### PUT /api/v1/products/{id}
- **Permisos**: Administrator, Supervisor

### GET /api/v1/products/{id}/lots
- **Permisos**: Administrator, Supervisor
- **Response 200**: `{ "lots": [{ "id": 7, "lotCode": "L-2210", "quantity": 50, "remainingQuantity": 32, "expirationDate": "2027-01-15", "receivedAt": "…" }], "undatedQuantity": 0 }`. Solo lotes con unidades, del que vence primero al último (RN-PROD-005).

### POST /api/v1/products/{id}/lots
- **Permisos**: Administrator, Supervisor
- **Request**: `{ "quantity": 20, "expirationDate": "2027-01-15", "lotCode": "L-2210" }` — le pone fecha a stock que ya existe sin moverlo. **Response 204**. **Errores**: 400 (cantidad mayor a las unidades sin fecha, fecha inválida o > 20 años), 404.

### PUT /api/v1/products/{id}/lots/{lotId}
- **Permisos**: Administrator, Supervisor
- **Request**: `{ "expirationDate": "2027-02-01", "lotCode": "L-2211", "quantity": 40 }` — `expirationDate` obligatoria; `quantity` opcional (unidades que le quedan al lote; sin ella no cambian; ver RN-PROD-004); un código en blanco lo borra. **Response 204**. **Errores**: 400 (fecha inválida, unidades ≤ 0 o mayores a las unidades sin fecha), 404 (producto o lote inexistente o ya agotado).

### DELETE /api/v1/products/{id}/lots/{lotId}
- **Permisos**: Administrator, Supervisor — quita el lote; el stock no cambia. **Response 204**.

### PATCH /api/v1/products/{id}/status
- **Permisos**: Administrator, Supervisor

### DELETE /api/v1/products/{id}
- **Permisos**: Administrator

## Modelo de Datos

### Tabla: `products`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| internal_code | VARCHAR(50) | NOT NULL, único **por local** (`UK_products_store_internal_code`) |
| barcode | VARCHAR(50) | NULLABLE, único **por local** (`UK_products_store_barcode`) |
| tax_rate | DECIMAL(5,2) | NOT NULL, DEFAULT 0.00 (porcentaje de IVA, `V2`) |
| unspsc_code | VARCHAR(50) | NULLABLE (clasificación UNSPSC para la DIAN, `V2`) |
| name | VARCHAR(100) | NOT NULL |
| description | VARCHAR(255) | NULLABLE |
| store_id | BIGINT | FK → stores(id), NOT NULL |
| category_id | BIGINT | FK → categories(id), NOT NULL |
| subcategory_id | BIGINT | FK → product_subcategories(id), NULLABLE |
| purchase_price | DECIMAL(12,2) | NOT NULL, DEFAULT 0.00 |
| sale_price | DECIMAL(12,2) | NOT NULL, DEFAULT 0.00 |
| quantity_available | INT | NOT NULL, DEFAULT 0 |
| min_stock | INT | NOT NULL, DEFAULT 0 |
| active | BOOLEAN | NOT NULL, DEFAULT TRUE |
| image_url | VARCHAR(255) | NULLABLE |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

### Tabla: `product_lots` (`V27`)
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| store_id | BIGINT | FK → stores(id), NOT NULL |
| product_id | BIGINT | FK → products(id), NOT NULL |
| lot_code | VARCHAR(50) | NULLABLE |
| quantity | INT | NOT NULL (unidades del lote en la última consolidación: lo que entró hasta que algo lo consolida; > 0 validado en el servicio. Ver RN-PROD-005) |
| expiration_date | DATE | NOT NULL |
| received_at | TIMESTAMP | NOT NULL |
| created_at | TIMESTAMP | NOT NULL |

**Índices**: `IDX_product_lots_product (product_id)`, `IDX_product_lots_store_expiration (store_id, expiration_date)`. Lo que queda de cada lote se deriva del stock (RN-PROD-005). `StorePurgeDao` borra los lotes antes que los productos del local.

**Constraints**:
- FK_products_store → stores(id), FK_products_category → categories(id), FK_products_subcategory → product_subcategories(id)
- UK_products_store_internal_code UNIQUE (store_id, internal_code), UK_products_store_barcode UNIQUE (store_id, barcode)
- CK_product_prices: `sale_price >= purchase_price`
- CK_product_stock: `quantity_available >= 0`

## Permisos
| Operación | Administrator | Supervisor | Seller |
|---|---|---|---|
| Crear | ✓ | ✓ | - |
| Leer | ✓ | ✓ | ✓ |
| Editar | ✓ | ✓ | - |
| Eliminar | ✓ | - | - |
