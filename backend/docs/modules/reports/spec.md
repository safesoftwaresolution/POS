<!-- spec-version: 1.1 | last-updated: 2026-10-16 -->
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

### GET /api/v1/reports/top-products
- **Permisos**: Admin
- **Query params**: dateFrom, dateTo, limit (default 10)

### GET /api/v1/reports/stock
- **Permisos**: Admin, Supervisor
- **Query params**: belowMinStock (boolean)

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
Este módulo no posee tablas propias. Ejecuta queries de solo lectura sobre las tablas de sales, sale_items, purchases, purchase_items, products e inventory_movements.

## Permisos
| Operación | Admin | Supervisor | Vendedor |
|---|---|---|---|
| Ver reportes | ✓ (todos) | ✓ (solo stock) | - |
