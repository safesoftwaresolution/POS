<!-- spec-version: 1.1 | last-updated: 2026-11-23 -->
# Módulo: Finanzas (Finance)

Estado financiero del local **en base caja** (cuenta el dinero cuando entra o sale), con egresos e ingresos manuales y costos fijos mensuales. Inspirado en el módulo de finanzas de Gastroflow.

## Dependencias
- **Requiere (read-only, por SQL)**: sales, sale_returns, credit_movements, purchases. No importa las Facades de esos módulos; es la misma excepción CQRS que `reports` (`FinanceReadDao`, `JdbcClient`, parámetros nombrados).
- **Requerido por**: ninguno
- **Datos propios**: `finance_movements`, `fixed_costs` (V48).

## Reglas de Negocio

### RN-FIN-001 — Ingresos en base caja
`income` = ventas cuyo método **no** es `CREDIT` + abonos de fiado (`credit_movements.type = 'PAYMENT'`) + **ingresos manuales** (`finance_movements.type = 'INCOME'`) − devoluciones de ventas que **no** eran a fiado.
Una venta a fiado no es ingreso hasta que se abona; la devolución de una venta a fiado baja la deuda y no devuelve dinero, por eso no resta. `otherIncome` es la parte de `income` que son ingresos manuales. Un aporte del dueño o un préstamo registrado como ingreso **sí suma a la utilidad**; si no debe contar como ganancia, no se registra como ingreso.

### RN-FIN-002 — Egresos y utilidad
`totalOutflow` = compras del período (`purchases.total_amount`) + egresos manuales (`finance_movements.type = 'EXPENSE'`) + costos fijos activos prorrateados. `profit` = `income` − `totalOutflow`.
Es base caja: una compra de mercancía resta cuando se compra, no cuando se vende; no hay costo de lo vendido (ver Pendientes).

### RN-FIN-003 — Costos fijos prorrateados y día de cobro
Cada costo fijo activo se reparte en 30 días: `mensual ÷ 30 × días del período`, con 2 decimales. Los inactivos no cuentan. No tienen fecha, así que la serie diaria no los incluye (sí el total).
`dueDay` (1 a 31, opcional) es el día del mes en que se paga. Es **solo un recordatorio**: la UI calcula la próxima fecha (en un mes más corto vale el último día: 31 → 28 o 30) y avisa cuánto falta, con alerta si faltan 5 días o menos. No genera movimientos ni cambia la utilidad. Un costo fijo no debe registrarse además como egreso: se contaría dos veces.

### RN-FIN-004 — Movimientos manuales (egreso o ingreso)
Tipo obligatorio (`EXPENSE` o `INCOME`), categoría obligatoria (texto libre, hasta 60; la UI sugiere categorías distintas por tipo), monto > 0, método de pago (por defecto `CASH`) y **fecha real** (por defecto hoy, no puede ser futura). El movimiento cuenta en el período de su `movement_date`, aunque se registre después. Se guarda quién lo registró (`username`). Al editar se puede cambiar el tipo.

### RN-FIN-005 — Períodos y comparativa
Sin fechas se usan los últimos 30 días; el máximo es 366 y `dateFrom` no puede ser posterior a `dateTo`; ambos extremos son días completos. Cada variación (`incomeVariationPct`, `outflowVariationPct`, `profitVariationPct`) compara contra el período inmediatamente anterior de **igual longitud**; con base 0 es 100 si hay valor y 0 si no.

### RN-FIN-006 — Alcance y permisos
Todo se filtra por el local de la sesión (`TenantContext`); un movimiento o costo de otro local responde 404. Solo `ADMINISTRATOR` (son datos de dinero, como `reports`). Un costo fijo no repite nombre dentro del local (sin distinguir mayúsculas).

## API Endpoints
Todos bajo `/api/v1/finance`, **Permisos**: Admin.

| Método y ruta | Descripción |
|---|---|
| `GET /summary?dateFrom&dateTo` | `FinanceSummaryDto`: `income`, `otherIncome`, `purchases`, `expenses`, `fixedCosts`, `totalOutflow`, `profit`, variaciones, `expensesByCategory`, `incomeByPaymentMethod`, `daily` (un punto por día, ceros incluidos) |
| `GET /movements?dateFrom&dateTo&page&size` | Movimientos manuales del período, más reciente primero (tamaño máx. 100) |
| `POST /movements` · `PUT /movements/{id}` · `DELETE /movements/{id}` | Alta (201), edición y baja; el cuerpo lleva `type`, `category`, `description`, `amount`, `paymentMethod`, `movementDate` |
| `GET /fixed-costs` | Costos fijos del local por nombre |
| `POST /fixed-costs` · `PUT /fixed-costs/{id}` · `DELETE /fixed-costs/{id}` | Alta (201), edición (incluye `dueDay` y `active`) y baja. En el `PUT`, un `dueDay` vacío borra el día de cobro |

## Modelo de Datos
- `finance_movements(id, store_id, type, category, description, amount, payment_method, movement_date, user_id, username, created_at, updated_at)`; índice `IDX_finance_movements_store_date (store_id, movement_date)`. `type` es `EXPENSE` o `INCOME` (validado en el servicio, sin `CHECK` por H2).
- `fixed_costs(id, store_id, name, monthly_amount, due_day, active, created_at, updated_at)`; `UK_fixed_costs_store_name (store_id, name)`; `due_day` es `NULL` si no tiene día fijo.
- `user_id` queda sin usar por ahora (se guarda el `username` de la sesión).

## Frontend
Página `/finance` (`FinanceBento`, solo `ADMINISTRATOR`, enlace «Finanzas» en el menú). Arriba, justo bajo el título, tres botones con su explicación: **Registrar egreso**, **Registrar ingreso** y **Nuevo costo fijo**. Después: rango de fechas, tarjetas de ingresos / egresos / utilidad con su variación (la de ingresos indica cuánto son manuales), la lista de movimientos manuales y la de costos fijos (con su día de cobro, la próxima fecha y una nota de cómo funcionan), el flujo diario y los desgloses.

## Pendientes (backlog)
- Cuentas por pagar: estado de pago y vencimiento en `purchases` (hoy toda compra cuenta como egreso al comprar).
- Marcar un costo fijo como pagado en el mes y alertar los vencidos (hoy el día de cobro es solo un recordatorio).
- Costo de lo vendido (utilidad contable): exige guardar el costo en `sale_items` al vender (toca `sales`).
- PDF del estado financiero y exportación.
- Movimientos recurrentes y categorías administrables como catálogo.
- Separar los aportes del dueño de los ingresos (como los «aportes» de Gastroflow).
- Auditoría de cambios en movimientos y costos fijos.
