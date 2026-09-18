-- V13__Sale_Items_Tax_And_Electronic_Invoices_Store.sql
-- 1. Impuestos en items de venta: registra de forma inmutable la tasa y monto de IVA
-- aplicados a cada linea de la venta al momento de expedirse el documento.
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS tax_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00;
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00;

-- 2. Aislamiento multi-tenant en electronic_invoices:
-- Vincula directamente la factura electronica al local (store) para consultas y auditorias
-- aisladas por negocio sin requerir joins complejos.
ALTER TABLE electronic_invoices ADD COLUMN IF NOT EXISTS store_id BIGINT;

-- Poblar store_id retroactivamente a partir de la venta asociada
UPDATE electronic_invoices e
SET store_id = (SELECT s.store_id FROM sales s WHERE s.id = e.sale_id)
WHERE e.store_id IS NULL;

-- Asignar local por defecto para registros huerfanos si existieran
UPDATE electronic_invoices
SET store_id = (SELECT id FROM stores WHERE email = 'default@platform.internal')
WHERE store_id IS NULL;

-- Aplicar restricciones de no nulidad, clave foranea e indice
ALTER TABLE electronic_invoices ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE electronic_invoices ADD CONSTRAINT FK_electronic_invoices_store FOREIGN KEY (store_id) REFERENCES stores(id);
CREATE INDEX IF NOT EXISTS IDX_electronic_invoices_store ON electronic_invoices(store_id);
CREATE INDEX IF NOT EXISTS IDX_electronic_invoices_store_status ON electronic_invoices(store_id, status);
