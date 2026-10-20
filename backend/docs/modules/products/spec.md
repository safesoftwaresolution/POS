<!-- spec-version: 1.1 | last-updated: 2026-09-16 -->
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

## Requerimientos Funcionales

### RF-PROD-001 — Crear Producto
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Campos obligatorios: internalCode (único por tienda), name, categoryId, purchasePrice, salePrice.
  - Campos opcionales: barcode (único por tienda si ingresado), subcategoryId (ver RN-PROD-003), description, imageUrl.
  - Stock inicial: 0 (se alimenta vía módulo de Compras o Inventario).
  - Validar RN-PROD-001 (precio venta ≥ precio compra).

### RF-PROD-002 — Editar Producto
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Editable: name, description, categoryId, purchasePrice, salePrice, barcode, minStock, imageUrl.
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
  { "internalCode": "PROD-002", "barcode": "7701112223334", "name": "Papas Fritas", "categoryId": 2, "subcategoryId": 7, "purchasePrice": 0.50, "salePrice": 0.90, "minStock": 20 }
  ```
- **Response 201**: Objeto producto creado
- **Errores**: 400 (código duplicado, RN-PROD-001 violado, subcategoría inconsistente con la categoría)

### PUT /api/v1/products/{id}
- **Permisos**: Administrator, Supervisor

### PATCH /api/v1/products/{id}/status
- **Permisos**: Administrator, Supervisor

### DELETE /api/v1/products/{id}
- **Permisos**: Administrator

## Modelo de Datos

### Tabla: `products`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| internal_code | VARCHAR(50) | UNIQUE, NOT NULL |
| barcode | VARCHAR(50) | UNIQUE, NULLABLE |
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
