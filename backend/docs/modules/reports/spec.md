<!-- spec-version: 1.2 | last-updated: 2026-10-25 -->
# Módulo: Reportes (Reports)

## Dependencias
- **Requiere (read-only)**: sales, purchases, inventory, products
- **Requerido por**: ninguno

## Reglas de Negocio
Este módulo no define reglas de negocio propias. Solo consulta datos de otros módulos.

- **Sin dependencia de código**: lee las tablas por SQL (`JdbcClient`, parámetros nombrados) y no importa las Facades de los otros módulos; es la excepción CQRS declarada en `_architecture.md`. Su interfaz pública se llama `ReportService` (no `ReportFacade`).
- **Alcance por local**: todas las consultas filtran por el `storeId` de la sesión.
- **Período por defecto**: si no se envían `dateFrom`/`dateTo` se usan los **últimos 30 días** (no \

## Requerimientos Funcionales

### RF-REP-001 — Reporte de Ventas por Período
- **Prioridad**: Alta
- Rango de fechas, total vendido, cantidad de transacciones, desglose por vendedor.

### RF-REP-002 — Reporte de Productos Más Vendidos
- **Prioridad**: Media
- Top N productos por cantidad vendida en un período.

### RF-REP-003 — Reporte de Inventario Actual (Stock)
- **Prioridad**: Alta
- Listado de productos con stock actual, stock mínimo, estado de alerta.
- Productos con stock ≤ minStock se marcan como "Bajo Stock".

### RF-REP-004 — Reporte de Rentabilidad
- **Prioridad**: Media
- Margen de utilidad por producto: `(salePrice - purchasePrice) × cantidadVendida`.

### RF-REP-005 — Reporte de Compras por Período
- **Prioridad**: Media
- Total invertido en compras, desglose por proveedor.

## API Endpoints

### GET /api/v1/reports/sales
- **Permisos**: Admin
- **Query params**: dateFrom, dateTo

### RF-REP-008 — Mapa de Calor de Ventas
- **Prioridad**: Media
- Ventas agrupadas por **día de la semana ISO** (1 = lunes … 7 = domingo) y **hora** (0-23) del `created_at`, con la cantidad de ventas y el monto de cada franja. Solo devuelve las franjas con ventas (el cliente rellena las vacías) y está ordenado por día y hora. Alcance: el local de la sesión.
- Sin fechas usa los últimos 30 días. El SQL usa `EXTRACT(ISODOW …)` y `EXTRACT(HOUR …)`, válidos en H2 y PostgreSQL; el alias de la hora es `hourOfDay` porque `hour` es palabra reservada en H2. `SalesHeatmapReportTest` ejecuta la consulta real contra el esquema Flyway.

### GET /api/v1/reports/sales-heatmap
- **Permisos**: Admin (son datos de dinero, como `/reports/sales`)
- **Query params**: dateFrom, dateTo
- **Respuesta**: `[{ dayOfWeek, hourOfDay, transactionCount, totalSold }]`

### GET /api/v1/reports/top-products
- **Permisos**: Admin
- **Query params**: dateFrom, dateTo, limit (default 10)

### GET /api/v1/reports/stock
- **Permisos**: Admin, Supervisor
- **Query params**: belowMinStock (boolean)

### RF-REP-007 — Lotes Vencidos y Próximos a Vencer
- **Prioridad**: Media
- Una fila **por lote** (`product_lots`, ver `products/spec.md` RN-PROD-004/005) de productos **activos** que todavía **tiene unidades** y cuyo vencimiento ya pasó o cae dentro de la ventana, del que vence primero al último (empate: nombre y luego lote). Lotes agotados, productos inactivos o sin lotes no aparecen; el alcance es el local de la sesión.
- `lotRemaining` es lo que queda del lote, no lo que entró: se reparte el stock del producto asumiendo que se vende primero lo que vence primero. La consulta lo calcula con funciones de ventana (`SUM() OVER`, `LEAST`/`GREATEST`, válidas en H2 y PostgreSQL) y repite la regla de `ProductLotAllocator`; `ExpiringProductsReportTest.sqlAndJavaAllocationAgree` comprueba que coincidan.
- Cada fila trae `daysToExpire` (negativo si ya venció) y `status`: `EXPIRED` (< 0), `TODAY` (0) o `EXPIRING`. Se calculan en el servicio con `LocalDate.now()` del servidor (zona del JVM), no en SQL.
- La ventana `days` es opcional: por defecto 30, acotada a 1..365. Máximo 500 filas.

### GET /api/v1/reports/expiring
- **Permisos**: Admin, Supervisor
- **Query params**: days (opcional, 1..365, default 30)
- **Response 200**: `[{ "productId": 1, "productName": "Leche entera 1L", "internalCode": "PROD-001", "lotId": 7, "lotCode": "L-2210", "expirationDate": "2026-11-15", "daysToExpire": 12, "lotRemaining": 24, "quantityAvailable": 60, "status": "EXPIRING" }]`. La UI vive en el Dashboard (tarjeta "Próximos a vencer").

### GET /api/v1/reports/expiring/pdf
- **Permisos**: Admin, Supervisor
- **Query params**: days (opcional, 1..365, default 30)
- **Response 200**: `application/pdf` (`Content-Disposition: inline; filename="vencimientos.pdf"`). Mismo contenido que `/expiring`: una fila por lote con producto, código, lote, fecha, estado, unidades que quedan del lote y stock total del producto; vencidos en rojo y los que vencen en 7 días o menos en ámbar. Sin lotes en la ventana, el PDF lo dice. Se dibuja con OpenPDF en `ExpiringReportPdfRenderer` (sin datos propios: reutiliza `getExpiringProductsReport`). La UI es el botón «Vencimientos (PDF)» de Inventario → Stock (Admin y Supervisor), con ventana de 30, 60 o 90 días.

### GET /api/v1/reports/profitability
- **Permisos**: Admin
- **Query params**: dateFrom, dateTo

### GET /api/v1/reports/purchases
- **Permisos**: Admin
- **Query params**: dateFrom, dateTo

### RF-REP-006 — Cierre de Caja
- **Prioridad**: Media
- Efectivo esperado en caja (suma de ventas `CASH` del período), total vendido por cada método de pago y desglose del efectivo por vendedor para rendir cuentas por cajero.

### GET /api/v1/reports/cash-closing
- **Permisos**: Admin
- **Query params**: dateFrom, dateTo
- **Response 200**: `CashClosingReportDto { dateFrom, dateTo, totalByPaymentMethod, expectedCash, cashBySeller }`. La UI vive en `ReportsBento`.

## Modelo de Datos
Este módulo no posee tablas propias. Ejecuta queries de solo lectura sobre las tablas de sales, sale_items, purchases, purchase_items, products, product_lots e inventory_movements.

## Permisos
| Operación | Admin | Supervisor | Vendedor |
|---|---|---|---|
| Ver reportes | ✓ (todos) | ✓ (stock y vencimientos) | - |
