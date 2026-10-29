<!-- spec-version: 1.0 | last-updated: 2026-10-28 -->
# Módulo: Servicios (Services)

Catálogo de servicios que un negocio cobra y factura sin inventario (spa, peluquería, consultoría, mantenimiento…). Un servicio se vende en el POS igual que un producto, pero **no tiene stock, lotes ni movimientos de inventario**.

## Dependencias
- **Requiere**: categories (categoría global obligatoria, igual que products)
- **Requerido por**: sales (vende servicios vía `ServiceFacade`), billing (indirecto: lee la copia guardada en `sale_items`, no consulta el catálogo)

## Decisión de diseño
Se eligió un módulo propio (y no un `item_type` dentro de `products`) para que el catálogo de servicios pueda crecer sin tocar productos: duración y agenda de citas, profesionales y comisiones, paquetes de sesiones. La venta referencia el servicio por `service_id` y guarda una copia de lo facturado, de modo que esas ampliaciones se agregan como tablas o columnas nuevas sin cambiar las ventas ya emitidas.

## Reglas de Negocio

### RN-SRV-001 — Sin Inventario
- Vender un servicio no valida existencias, no registra movimiento de inventario y no consume lotes. Devolver un servicio solo reembolsa (RN-SALE-004).

### RN-SRV-002 — Código Único por Tienda
- `code` es único por tienda (`UK_services_store_code`); dos tiendas pueden usar el mismo código. No cambia después de creado.

### RN-SRV-003 — Precio e IVA
- `price` es el precio de venta **con el IVA incluido** y debe ser mayor a cero. `tax_rate` va de 0 a 100 (por defecto 0; valores usuales 0, 5 y 19). El IVA se desglosa del subtotal igual que en productos.
- No existe precio de compra ni margen: no hay costo de inventario.

### RN-SRV-004 — Datos DIAN
- `unspsc_code` (opcional) y `unit_measure_code` (por defecto `94`, unidad) viajan a la factura electrónica. Sin UNSPSC se factura con el código genérico `999`. Los códigos de unidad para servicios por hora u otras medidas deben validarse contra el catálogo de Factus.

### RN-SRV-005 — Baja Lógica
- Eliminar un servicio lo deja inactivo (`active = false`); nunca se borra la fila, porque las ventas y devoluciones ya emitidas lo referencian. Un servicio inactivo no se puede vender.
- No se puede eliminar una categoría global que tenga servicios activos (`CategoryDeleteValidator`).

### RN-SRV-006 — Aislamiento por Tienda
- Todo servicio pertenece a una tienda (`store_id`, vía `TenantContext`). Consultar o modificar el servicio de otra tienda responde como inexistente (404).

### RN-SRV-007 — Duración Opcional
- `duration_minutes` es opcional (mayor a cero si se envía). Hoy es informativa; la usará la agenda de citas (fase futura).

## Requerimientos Funcionales

### RF-SRV-001 — Gestionar el Catálogo
- Crear, consultar, editar, activar/desactivar y dar de baja servicios; listar con búsqueda parcial por nombre o código y filtros por categoría y estado.
- Editar un servicio **no altera** las ventas ya emitidas: cada línea guarda el nombre, precio, IVA, unidad de medida y UNSPSC con que se vendió.

### RF-SRV-002 — Vender Servicios
- Una línea de venta indica `serviceId` en lugar de `productId` (exactamente uno de los dos). Ver `sales/spec.md`, RF-SALE-001.

## API Endpoints

### GET /api/v1/services
- **Permisos**: Admin, Supervisor, Vendedor
- **Query params**: `search` (nombre o código), `categoryId`, `active`, `page`, `size`

### GET /api/v1/services/{id}
- **Permisos**: Admin, Supervisor, Vendedor. 404 si no existe o es de otra tienda.

### POST /api/v1/services
- **Permisos**: Admin, Supervisor
- **Request**:
  ```json
  {
    "code": "SRV-MASAJE", "name": "Masaje relajante 60 min", "description": "Cuerpo completo",
    "categoryId": 3, "price": 119000.00, "taxRate": 19.00,
    "unspscCode": "90111500", "unitMeasureCode": "94", "durationMinutes": 60
  }
  ```
- **Response 201**: `ServiceDto` (incluye `categoryName` y `active`).
- **Errores**: 400 (código vacío o repetido en la tienda, nombre vacío o de más de 100 caracteres, categoría inexistente, precio ≤ 0, IVA fuera de 0–100, duración ≤ 0).

### PUT /api/v1/services/{id}
- **Permisos**: Admin, Supervisor
- Solo cambia los campos enviados (`name`, `description`, `categoryId`, `price`, `taxRate`, `unspscCode`, `unitMeasureCode`, `durationMinutes`). Las mismas validaciones que al crear. 404 si no existe.

### PATCH /api/v1/services/{id}/status
- **Permisos**: Admin, Supervisor
- **Request**: `{ "active": true }`. 400 si falta `active`; 404 si no existe. 204.

### DELETE /api/v1/services/{id}
- **Permisos**: solo Admin. Baja lógica (RN-SRV-005). 204.

## Modelo de Datos

### Tabla: `services` (`V29`)
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| store_id | BIGINT | FK → stores(id), NOT NULL |
| code | VARCHAR(50) | NOT NULL |
| name | VARCHAR(100) | NOT NULL |
| description | VARCHAR(255) | NULLABLE |
| category_id | BIGINT | FK → categories(id), NOT NULL |
| price | DECIMAL(12,2) | NOT NULL (IVA incluido) |
| tax_rate | DECIMAL(5,2) | NOT NULL, DEFAULT 0 |
| unspsc_code | VARCHAR(50) | NULLABLE |
| unit_measure_code | VARCHAR(10) | NOT NULL, DEFAULT '94' |
| duration_minutes | INT | NULLABLE |
| active | BOOLEAN | NOT NULL, DEFAULT TRUE |
| created_at / updated_at | TIMESTAMP | NOT NULL |

**Constraints**: `UK_services_store_code` (`store_id`, `code`), `FK_services_store`, `FK_services_category`. **Índice**: `IDX_services_store_active` (`store_id`, `active`).

## Permisos
| Operación | Admin | Supervisor | Vendedor |
|---|---|---|---|
| Listar / consultar | ✓ | ✓ | ✓ |
| Crear / editar / activar | ✓ | ✓ | - |
| Eliminar (baja lógica) | ✓ | - | - |

## Fuera de alcance (fases futuras)
- Agenda de citas y disponibilidad (usará `duration_minutes`).
- Profesionales que prestan el servicio y comisiones (`sale_items.staff_id`).
- Paquetes o combos de sesiones.
- Cantidades decimales (1,5 horas): hoy `quantity` es entera.
- Reportes de servicios más vendidos (los reportes actuales de productos excluyen las líneas de servicio).
