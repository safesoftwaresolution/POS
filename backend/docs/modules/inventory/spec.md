<!-- spec-version: 1.1 | last-updated: 2026-10-16 -->
# Módulo: Inventario (Inventory)

## Dependencias
- **Requiere**: products, users, notifications (alerta de stock bajo), shared/email
- **Requerido por**: sales, purchases, reports

## Reglas de Negocio

### RN-INV-001 — Prohibición de Inventario Negativo
- `quantity_available >= 0` siempre. Si una operación resulta en stock negativo → bloquear transacción.

### RN-INV-002 — Actualización Automática de Inventario
- Compra confirmada → stock sube. Venta completada → stock baja. Siempre atómico.
- **Concurrencia**: el stock cambia con `ProductFacade.adjustStock`, un único `UPDATE … SET quantity_available = quantity_available + :delta WHERE … AND quantity_available + :delta >= 0` acotado al local de la sesión. La fila queda bloqueada hasta el commit: dos movimientos concurrentes sobre el mismo producto se serializan, no hay lost-update ni sobreventa y el stock nunca es negativo (si el UPDATE no afecta filas: 404 si el producto no es del local, 400 si faltaría stock). `previous_stock`/`new_stock` del kardex salen de la BD, no de la lectura inicial. (Antes este spec y el README afirmaban un bloqueo que no existía; el primer arreglo con `SELECT … FOR UPDATE` tampoco bastaba porque la venta ya tenía el producto cargado. Cubierto por `StockConcurrencyTest`.)

### RN-INV-003 — Alerta de Stock Bajo
- Cuando un movimiento hace que el stock **cruce** el mínimo hacia abajo (antes estaba por encima de `min_stock` y ahora está igual o por debajo), `LowStockAlertNotifier` publica una notificación `LOW_STOCK` en la app y envía un correo a los `ADMINISTRATOR` y `SUPERVISOR` activos del local. Se avisa una sola vez, no en cada movimiento posterior.

## Requerimientos Funcionales

### RF-INV-001 — Registrar Entrada de Inventario (Manual)
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Campos: productId, quantity (> 0), reason.
  - Tipo de movimiento: `ENTRADA` (el código también acepta el alias `ENTRY`).
  - Stock nuevo = stock actual + quantity.
  - Registrar movimiento con previous_stock y new_stock.

### RF-INV-002 — Registrar Salida de Inventario (Manual)
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Campos: productId, quantity (> 0), reason.
  - Tipo de movimiento: `SALIDA` (alias `EXIT`).
  - Validar RN-INV-001: stock resultante ≥ 0.
  - Registrar movimiento con previous_stock y new_stock.

### RF-INV-003 — Registrar Ajuste de Inventario
- **Prioridad**: Media
- Ajuste manual tras conteo físico. Tipo: `AJUSTE` (alias `ADJUSTMENT`).
- **Limitación**: el ajuste siempre **suma** (`delta = +quantity`); no puede disminuir stock. Para bajar stock se usa `SALIDA`. Pendiente decidir si el ajuste es un delta con signo o un valor absoluto contado.
- **Tipos aceptados por el servicio**: suman `ENTRADA/ENTRY, COMPRA, AJUSTE/ADJUSTMENT, DEVOLUCION_VENTA`; restan `SALIDA/EXIT, VENTA, RETURN, DEVOLUCION` (devolución al proveedor). Cualquier otro valor → 400. El texto se guarda tal como llega (en mayúsculas), así que el kardex mezcla alias (`ENTRY` y `ENTRADA`); pendiente normalizar a un enum.

### RF-INV-004 — Consultar Historial de Movimientos (Kardex)
- **Prioridad**: Alta
- Filtros: productId, movementType, rango de fechas.
- Mostrar: producto, tipo, cantidad, stock anterior, stock nuevo, usuario, fecha.

## API Endpoints

### POST /api/v1/inventory/movements
- **Permisos**: Admin, Supervisor
- **Request**:
  ```json
  { "productId": 1, "movementType": "SALIDA", "quantity": 3, "reason": "Merma por vencimiento" }
  ```
- **Response 201**:
  ```json
  { "id": 1500, "productName": "Coca Cola 350ml", "movementType": "SALIDA", "quantity": 3, "previousStock": 45, "newStock": 42, "reason": "Merma por vencimiento", "user": "supervisor1", "createdAt": "2026-07-07T10:49:00Z" }
  ```
- **Errores**: 400 (cantidad inválida, stock negativo resultante)

### GET /api/v1/inventory/movements
- **Permisos**: Admin, Supervisor, Vendedor (solo lectura)
- **Query params**: productId, movementType, dateFrom, dateTo, page, size

## Modelo de Datos

### Tabla: `inventory_movements`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| product_id | BIGINT | FK → products(id), NOT NULL |
| store_id | BIGINT | FK → stores(id) (`V9`) |
| movement_type | VARCHAR(20) | NOT NULL. Valores aceptados: ver RF-INV-003 |
| quantity | INT | NOT NULL (siempre positivo) |
| previous_stock | INT | NOT NULL |
| new_stock | INT | NOT NULL |
| reason | VARCHAR(255) | NULLABLE |
| user_id | BIGINT | FK → users(id), NOT NULL |
| created_at | TIMESTAMP | NOT NULL |

**Constraints**: FK_movements_product, FK_movements_user

## Permisos
| Operación | Admin | Supervisor | Vendedor |
|---|---|---|---|
| Crear movimiento | ✓ | ✓ | - |
| Leer historial | ✓ | ✓ | ✓ |
