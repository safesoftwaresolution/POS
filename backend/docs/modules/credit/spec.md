<!-- spec-version: 1.1 | last-updated: 2026-10-30 -->
# Módulo: Fiado (Credit)

Cuentas de fiado de los clientes: quién debe cuánto, ventas "pago después" y abonos. Una venta a fiado suma su total a la deuda del cliente; pagar con cualquier otro método no la toca; un abono la baja.

## Dependencias
- **Requiere**: customers (el cliente dueño de la cuenta), users (firma los movimientos), audit (cambios de cupo)
- **Requerido por**: sales (vende a fiado y descuenta la deuda en devoluciones)

## Reglas de Negocio

### RN-CRE-001 — Una Cuenta por Cliente
- Cada cliente tiene como mucho una cuenta por local (`UK_credit_accounts_store_customer`), que se crea al fiarle por primera vez o al fijarle un cupo. Sin cuenta, el cliente debe cero.
- `balance` es lo que el cliente debe hoy. Solo cambia con las sentencias atómicas del repositorio (`addDebt`, `reduceDebt`), nunca mutando la entidad: dos operaciones simultáneas sobre la misma cuenta no pierden ninguna.

### RN-CRE-002 — Libro de Movimientos Inmutable
- Cada cambio de saldo deja un `credit_movements` que no se edita ni se borra, con el monto (siempre positivo), el saldo resultante (`balance_after`) y quién lo registró (id y nombre de usuario). Tipos: `CHARGE` (venta a fiado), `PAYMENT` (abono) y `RETURN` (devolución de una venta a fiado).

### RN-CRE-003 — Cupo
- `credit_limit` es opcional por cliente: sin cupo se puede fiar sin límite; con cupo, un fiado que dejaría la deuda por encima se rechaza (400) y, si venía de una venta, **toda la venta se revierte** (incluido el stock). El control es atómico: dos ventas simultáneas no pueden pasarse del cupo.
- Fijar o quitar el cupo (`PUT /credit/accounts/{id}/limit`) es de administrador y supervisor y queda en la bitácora de `audit` (`CREDIT_ACCOUNT`). Bajar el cupo por debajo de la deuda actual no obliga a nada: solo impide fiar más.

### RN-CRE-004 — Abonos
- Un abono debe ser mayor a cero, no puede superar la deuda actual (no hay saldo a favor) y no puede pagarse con el método `CREDIT`. Si el cliente no debe nada, se rechaza. Se registra con su método de pago (por defecto `CASH`), una nota opcional y quién lo recibió.
- Cualquier rol de venta registra abonos: quien atiende el mostrador cobra, aunque otro vendedor haya fiado.

### RN-CRE-005 — Fecha Límite
- Cada `CHARGE` guarda `due_date`, la fecha acordada de pago: la indica quien vende (`creditDueDate`) o, si no, 30 días desde la venta. La usa la factura electrónica a crédito (`payment_due_date`) y la usarán las alertas de mora (fase futura).

### RN-CRE-006 — Devoluciones
- Devolver productos de una venta a fiado baja la deuda por el valor devuelto, **hasta lo que aún se debe**. Si el cliente ya había pagado esa parte, la diferencia se le devuelve en dinero (fuera de la cuenta). Devolver una venta pagada con otro método no toca la cuenta.

### RN-CRE-007 — Cliente con Deuda
- Un cliente con deuda pendiente no se puede dar de baja (`CustomerDeleteValidator`, 409 en la respuesta de la baja).

### RN-CRE-008 — Visibilidad por Rol
- `ADMINISTRATOR` y `SUPERVISOR` ven todas las cuentas del local. Un `SELLER` solo lista y abre el detalle de las cuentas a cuyos clientes **él** les fió (tiene un `CHARGE` registrado por él); las demás responden como inexistentes (404).
- Saldo y cupo de un cliente (`GET /credit/accounts/{customerId}`) los consulta cualquier rol de venta: hace falta para fiar en el POS sin pasarse del cupo.
- Todo es por local (`TenantContext`); la cuenta de otro local responde como inexistente.

## API Endpoints

### GET /api/v1/credit/accounts
- **Permisos**: Admin, Supervisor, Vendedor (este último, solo sus cuentas)
- **Query params**: `search` (nombre o identificación), `onlyWithBalance` (true por defecto), `page`, `size`
- **Response 200**: página de `CreditAccountDto` (`customerId`, `customerName`, `identification`, `balance`, `creditLimit`, `availableCredit`, `lastMovementAt`), de mayor a menor deuda.

### GET /api/v1/credit/accounts/{customerId}
- **Permisos**: Admin, Supervisor, Vendedor. Saldo y cupo (cero y sin cupo si nunca se le fió). 404 si el cliente no existe.

### GET /api/v1/credit/accounts/{customerId}/movements
- **Permisos**: Admin, Supervisor, Vendedor (solo cuentas que fió). Página de `CreditMovementDto`, del más reciente al más antiguo.

### POST /api/v1/credit/accounts/{customerId}/payments
- **Permisos**: Admin, Supervisor, Vendedor
- **Request**: `{ "amount": 20000.00, "paymentMethod": "CASH", "note": "Pagó el viernes" }`
- **Response 201**: `CreditMovementDto` con `balanceAfter`.
- **Errores**: 400 (monto ≤ 0, supera la deuda, sin deuda, método `CREDIT`), 404.

### PUT /api/v1/credit/accounts/{customerId}/limit
- **Permisos**: Admin, Supervisor
- **Request**: `{ "creditLimit": 200000.00 }` (null quita el límite). 400 si es negativo.

## Modelo de Datos

### Tabla: `credit_accounts` (`V31`)
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| store_id | BIGINT | FK → stores(id), NOT NULL |
| customer_id | BIGINT | FK → customers(id), NOT NULL |
| balance | DECIMAL(12,2) | NOT NULL, DEFAULT 0 |
| credit_limit | DECIMAL(12,2) | NULLABLE (sin límite) |
| created_at / updated_at | TIMESTAMP | NOT NULL |

**Constraints**: `UK_credit_accounts_store_customer` (`store_id`, `customer_id`). **Índice**: `IDX_credit_accounts_store_balance`.

### Tabla: `credit_movements` (`V31`)
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| store_id | BIGINT | NOT NULL |
| account_id | BIGINT | FK → credit_accounts(id), NOT NULL |
| customer_id | BIGINT | NOT NULL |
| type | VARCHAR(20) | NOT NULL (`CHARGE`, `PAYMENT`, `RETURN`) |
| amount | DECIMAL(12,2) | NOT NULL (positivo) |
| balance_after | DECIMAL(12,2) | NOT NULL |
| sale_id | BIGINT | NULLABLE (ventas a fiado y devoluciones) |
| payment_method | VARCHAR(30) | NULLABLE (abonos) |
| due_date | DATE | NULLABLE (ventas a fiado) |
| note | VARCHAR(255) | NULLABLE |
| user_id / username | BIGINT / VARCHAR(50) | NULLABLE (sin FK: el historial sobrevive al usuario) |
| created_at | TIMESTAMP | NOT NULL |

**Índices**: `IDX_credit_movements_account` (`account_id`, `created_at`), `IDX_credit_movements_sale` (`sale_id`).

`V31` también agrega el valor `CREDIT` ("Fiado (pago después)") al tema de parámetros `PAYMENT_METHODS`, para que el POS lo ofrezca.

## Permisos
| Operación | Admin | Supervisor | Vendedor |
|---|---|---|---|
| Vender a fiado | ✓ | ✓ | ✓ |
| Ver saldo y cupo de un cliente | ✓ | ✓ | ✓ |
| Listar cuentas y ver movimientos | ✓ todas | ✓ todas | Solo las que fió |
| Registrar abonos | ✓ | ✓ | ✓ |
| Fijar el cupo | ✓ | ✓ | - |

## Fuera de alcance (fases futuras)
(El cierre de caja con abonos y la factura a crédito ya están hechos: ver `reports/spec.md` y `billing/spec.md`, RN-BILL-021.)
- Pantalla de Fiado y método Fiado en el POS (fase 3).
- Pago parcial al vender (parte de contado, parte a fiado), ajustes o condonación de deuda, alertas y reporte de mora, y recibo del abono.
