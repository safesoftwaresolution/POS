<!-- spec-version: 1.3 | last-updated: 2026-10-25 -->
# Módulo: Inventario (Inventory)

## Dependencias
- **Requiere**: products, categories (importación masiva), users, notifications (alerta de stock bajo), shared/email
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
  - Tipo de movimiento: `ENTRADA` (el código también acepta el alias `ENTRY`). En la práctica se escribe `ENTRY`: es la grafía de la importación (RF-INV-005), del "Stock inicial" al crear un producto y de la pantalla *Mover stock*.
  - Stock nuevo = stock actual + quantity.
  - Registrar movimiento con previous_stock y new_stock.
  - **Desde la UI** (Inventario → *Mover stock* → *Entrada*, solo Admin/Supervisor): sube stock **sin proveedor**, así no cuenta como compra en los reportes ni reescribe el costo (RN-PUR-002). Motivo obligatorio: *Inventario inicial*, *Mercancía que ya tenía*, *Bonificación u obsequio del proveedor* u *Otro* (exige nota). El `reason` se guarda como la etiqueta o `etiqueta: nota` (máx. 255 caracteres; el backend no lo valida). La cantidad es un entero entre 1 y 1.000.000.

### RF-INV-002 — Registrar Salida de Inventario (Manual)
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Campos: productId, quantity (> 0), reason.
  - Tipo de movimiento: `SALIDA` (alias `EXIT`).
  - Validar RN-INV-001: stock resultante ≥ 0.
  - Registrar movimiento con previous_stock y new_stock.
  - **Desde la UI** (*Mover stock* → *Salida*): motivo obligatorio — *Vencimiento*, *Daño o rotura*, *Pérdida o robo*, *Consumo propio*, *Muestra u obsequio*, *Devolución a proveedor* u *Otro* (exige nota). Escribe `SALIDA`, salvo *Devolución a proveedor*, que escribe `DEVOLUCION`. La pantalla bloquea una salida mayor al stock; el backend la revalida (400).

### RF-INV-003 — Registrar Ajuste de Inventario
- **Prioridad**: Media
- Ajuste manual tras conteo físico. Tipo: `AJUSTE` (alias `ADJUSTMENT`).
- **Limitación**: el ajuste siempre **suma** (`delta = +quantity`); no puede disminuir stock. Para bajar stock se usa `SALIDA`. Pendiente decidir si el ajuste es un delta con signo o un valor absoluto contado.
- **Desde la UI** (*Mover stock* → *Contar y ajustar*): se escribe la cantidad contada y la pantalla muestra "Sistema: X → Contado: Y (diferencia ±Z)". Z > 0 envía `AJUSTE`; Z < 0 envía `SALIDA` (el ajuste solo suma); Z = 0 no envía nada. El movimiento aplica la **diferencia** sobre el stock vigente, no fija el valor contado: una venta que ocurra mientras tanto se descuenta del conteo en vez de pisarse, igual que en el conteo por Excel (RF-INV-005). El `reason` queda como `Conteo físico: sistema X, contado Y` (más la nota opcional).
- **Grafías que escribe hoy el sistema**: `ENTRY` (importación, producto nuevo, *Entrada*), `SALIDA` (importación, *Salida*, conteo), `AJUSTE` (importación, conteo), `COMPRA`, `VENTA`, `DEVOLUCION_VENTA` (devolución de un cliente) y `DEVOLUCION` (devolución a proveedor desde *Salida*; antes nadie la escribía). Los alias se siguen aceptando y se rotulan igual en la pantalla *Movimientos*.
- **Tipos aceptados por el servicio**: suman `ENTRADA/ENTRY, COMPRA, AJUSTE/ADJUSTMENT, DEVOLUCION_VENTA`; restan `SALIDA/EXIT, VENTA, RETURN, DEVOLUCION` (devolución al proveedor). Cualquier otro valor → 400. El texto se guarda tal como llega (en mayúsculas), así que el kardex mezcla alias (`ENTRY` y `ENTRADA`); pendiente normalizar a un enum.

### RF-INV-004 — Consultar Historial de Movimientos (Kardex)
- **Prioridad**: Alta
- Filtros: productId, movementType, rango de fechas.
- Mostrar: producto, tipo, cantidad, stock anterior, stock nuevo, usuario, fecha.
- **Orden**: del más reciente al más antiguo (`createdAt` y luego `id`, ambos descendentes), fijado en el controlador. Sin eso la primera página traía los movimientos más antiguos. Cubierto por `InventoryMovementsOrderTest`.
- **Desde la UI**: pestaña *Movimientos* de Inventario (la ven también los vendedores): 20 por página, filtro por producto (búsqueda en el servidor) y rango de fechas, con refresco automático. No filtra por tipo: el backend compara el texto exacto y el kardex mezcla alias (`ENTRY`/`ENTRADA`, `SALIDA`/`EXIT`), así que el filtro daría resultados incompletos. El signo de la cantidad sale de `newStock - previousStock`, no del tipo.

### RF-INV-005 — Importación Masiva y Conteo Físico (Excel)
- **Prioridad**: Alta
- Implementación: `ProductImportFacade` → `ProductImportServiceImpl` (orquesta), `ProductImportWorkbook` (Apache POI: escribe la plantilla y lee el archivo), `ProductImportRowParser` (convierte celdas). Vive en `inventory` porque este módulo ya depende de `products` y es dueño del kardex.
- **Plantilla** (`.xlsx`): hoja `Instrucciones`, hoja `Productos` (fila 1 = encabezados) y hoja oculta `Listas` (fuente de los desplegables). Columnas: `Código interno *`, `Nombre *`, `Categoría *` (desplegable con las categorías globales), `Subcategoría`, `Código de barras`, `Precio de compra`, `Precio de venta *`, `IVA %` (0/5/19), `Stock`, `Stock mínimo`, `Descripción`, `Código UNSPSC`. `*` = obligatoria para productos nuevos. Las columnas de código tienen formato texto para que Excel no los pase a notación científica. Con `withProducts=true` la hoja trae los productos activos del local y su stock actual.
- **Lectura**: las columnas se reconocen por el encabezado (sin importar mayúsculas, tildes, `*` ni orden; con algunos alias). Solo `Código interno` es obligatoria como columna: un archivo de conteo puede traer solo código + stock. Filas vacías se ignoran. Límites: `.xlsx`, ≤ 2 MB, ≤ 5.000 filas. Números en texto aceptan `$`, espacios y separadores (`12.500`, `12.500,50`, `12,500.50`); un separador único seguido de 3 dígitos es de miles. El IVA acepta `19`, `19%` o `0.19` (celda con formato porcentaje).
- **Reglas por fila** (la llave es `internal_code` exacto dentro del local):
  - Código nuevo → se crea el producto (stock 0) y, si `Stock` > 0, un movimiento `ENTRY` "Stock inicial (importación masiva)". Defaults: compra 0, IVA 0, stock mínimo 5.
  - Código existente → se actualizan solo las celdas llenas (vacía = conservar). `Stock` se toma como **conteo físico**: la diferencia contra el stock leído al validar entra como `AJUSTE` (sube) o `SALIDA` (baja) con motivo "Conteo físico (importación masiva)". Vacío = no se toca. Si hubo ventas mientras se importaba, se descuentan del conteo (no se borran).
  - Primero se actualizan los datos y después el stock: `adjustStockInStore` hace flush del update pendiente antes de su `UPDATE` atómico y la entidad no se vuelve a ensuciar.
  - Subcategoría inexistente en esa categoría → se crea una vez (aviso en la vista previa). Si cambia la categoría y la subcategoría viene vacía, se quita (aviso).
  - Errores: obligatorios faltantes, números inválidos o negativos, stock no entero, IVA distinto de 0/5/19, categoría inexistente, código o barcode repetido en el archivo, barcode de otro producto del local, venta < compra (RN-PROD-001, con los valores ya combinados), largos máximos.
  - Producto existente inactivo → se actualiza y sigue inactivo (aviso).
  - Acción por fila: `CREATE`, `UPDATE`, `UNCHANGED` (no se llama a `updateProduct`) o `ERROR`.
- **Todo o nada**: la vista previa no escribe nada. Al importar se vuelve a validar; con una sola fila con error responde 422 con la vista previa y no guarda nada. Si todo está bien se aplica en **una transacción**; si algo falla a mitad (p. ej. stock insuficiente por una venta concurrente) se revierte todo y responde 409 con el número de fila. Reimportar el mismo archivo deja todo `UNCHANGED` y no genera movimientos.
- **Alertas**: durante la importación no se envía la alerta de stock bajo por producto (RN-INV-003): sería una ráfaga de correos y la alerta asíncrona saldría aunque la transacción se revierta. El resultado informa `lowStockProducts`.

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

### GET /api/v1/inventory/import/template
- **Permisos**: Admin, Supervisor
- **Query params**: `withProducts` (default `false`)
- **Response 200**: archivo `.xlsx` (`attachment`)

### POST /api/v1/inventory/import/preview
- **Permisos**: Admin, Supervisor
- **Request**: `multipart/form-data` con `file` (.xlsx)
- **Response 200** (no guarda nada):
  ```json
  { "totalRows": 3, "toCreate": 1, "toUpdate": 1, "unchanged": 0, "withErrors": 1,
    "rows": [
      { "rowNumber": 2, "internalCode": "P-0001", "name": "Arroz 500g", "action": "CREATE", "currentStock": null, "newStock": 40, "errors": [], "warnings": [] },
      { "rowNumber": 3, "internalCode": "P-0002", "name": "Leche", "action": "UPDATE", "currentStock": 10, "newStock": 7, "errors": [], "warnings": [] },
      { "rowNumber": 4, "internalCode": "P-0003", "name": null, "action": "ERROR", "currentStock": null, "newStock": 0, "errors": ["Nombre es obligatorio para un producto nuevo."], "warnings": [] } ] }
  ```
- **Errores**: 400 (no es un .xlsx válido, falta la columna `Código interno`, hoja vacía, > 2 MB o > 5.000 filas), 413 (supera el límite de subida)

### POST /api/v1/inventory/import
- **Permisos**: Admin, Supervisor
- **Request**: `multipart/form-data` con `file` (.xlsx)
- **Response 200**:
  ```json
  { "totalRows": 120, "created": 100, "updated": 15, "unchanged": 5, "stockMovements": 110, "subcategoriesCreated": 2, "lowStockProducts": 7 }
  ```
- **Errores**: 400 (igual que preview), 409 (algo cambió mientras se importaba; no se guardó nada), 422 (`{ "error", "message", "preview" }`: hay filas con errores; no se guardó nada)

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
| Importación masiva / conteo (plantilla, vista previa, importar) | ✓ | ✓ | - |
