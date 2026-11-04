-- V31__Customer_Credit.sql
-- Fiado: una cuenta por cliente con su saldo (lo que debe) y un libro de movimientos que no se edita ni se borra:
-- deuda por venta a fiado (CHARGE), abono (PAYMENT) y devolución de una venta a fiado (RETURN).
-- Sin CHECK ... IN para que sea segura en H2.
CREATE TABLE credit_accounts (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    balance DECIMAL(12,2) NOT NULL DEFAULT 0,
    credit_limit DECIMAL(12,2),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_credit_accounts_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT FK_credit_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT UK_credit_accounts_store_customer UNIQUE (store_id, customer_id)
);
CREATE INDEX IDX_credit_accounts_store_balance ON credit_accounts(store_id, balance);

CREATE TABLE credit_movements (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    balance_after DECIMAL(12,2) NOT NULL,
    sale_id BIGINT,
    payment_method VARCHAR(30),
    due_date DATE,
    note VARCHAR(255),
    user_id BIGINT,
    username VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_credit_movements_account FOREIGN KEY (account_id) REFERENCES credit_accounts(id)
);
CREATE INDEX IDX_credit_movements_account ON credit_movements(account_id, created_at);
CREATE INDEX IDX_credit_movements_sale ON credit_movements(sale_id);

-- Método de pago "Fiado" en el POS (extra_value = medio de pago DIAN: 1 = instrumento no definido).
INSERT INTO parameter_values (topic_id, code, label, extra_value, sort_order, active, created_at, updated_at) VALUES
    ((SELECT id FROM parameter_topics WHERE code = 'PAYMENT_METHODS'), 'CREDIT', 'Fiado (pago después)', '1', 5, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
