-- V48__Finance_Expenses_And_Fixed_Costs.sql
-- Módulo de finanzas (base caja): movimientos manuales del local (egresos e ingresos) y costos fijos mensuales.
-- Las ventas, compras y abonos de fiado ya existen en sus tablas; el resumen financiero los lee por SQL.
-- Sin CHECK ... IN (seguro en H2): el tipo (EXPENSE | INCOME) y el día de cobro (1..31) se validan en el servicio.
CREATE TABLE finance_movements (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    type VARCHAR(10) NOT NULL,
    category VARCHAR(60) NOT NULL,
    description VARCHAR(255),
    amount DECIMAL(12,2) NOT NULL,
    payment_method VARCHAR(30) NOT NULL DEFAULT 'CASH',
    movement_date DATE NOT NULL,
    user_id BIGINT,
    username VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_finance_movements_store FOREIGN KEY (store_id) REFERENCES stores(id)
);
CREATE INDEX IDX_finance_movements_store_date ON finance_movements(store_id, movement_date);

CREATE TABLE fixed_costs (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    monthly_amount DECIMAL(12,2) NOT NULL,
    due_day INT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_fixed_costs_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT UK_fixed_costs_store_name UNIQUE (store_id, name)
);
