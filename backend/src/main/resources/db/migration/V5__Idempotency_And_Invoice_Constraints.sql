-- V5__Idempotency_And_Invoice_Constraints.sql
-- Endurece los endpoints no idempotentes de escritura:
--  * Ventas y compras aceptan una clave de idempotencia (Idempotency-Key) unica.
--    Un reenvio (doble clic / reintento de red) con la misma clave devuelve la
--    transaccion original en lugar de crear un duplicado.
--  * Las compras no pueden registrar dos veces la misma factura de un proveedor.
-- NULL es distinto de NULL en indices unicos (PostgreSQL y H2), por lo que las
-- ventas/compras sin clave y las compras sin numero de factura no colisionan.

-- 1. Clave de idempotencia en ventas
ALTER TABLE sales ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(80);
ALTER TABLE sales ADD CONSTRAINT UK_sales_idempotency_key UNIQUE (idempotency_key);

-- 2. Clave de idempotencia en compras
ALTER TABLE purchases ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(80);
ALTER TABLE purchases ADD CONSTRAINT UK_purchases_idempotency_key UNIQUE (idempotency_key);

-- 3. Unicidad de factura de proveedor (evita registrar dos veces la misma compra).
--    Los NULL siguen permitidos y no colisionan entre si.
ALTER TABLE purchases ADD CONSTRAINT UK_purchases_supplier_invoice UNIQUE (supplier_id, invoice_number);
