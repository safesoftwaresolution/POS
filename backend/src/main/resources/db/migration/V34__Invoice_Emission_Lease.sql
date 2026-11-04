-- V34__Invoice_Emission_Lease.sql
-- La llamada al proveedor de facturacion sale de la transaccion: la emision se reserva con un lease en la fila
-- de la factura (processing_started_at), se cuenta cada intento y se programa el siguiente reintento automatico.
-- Seguro en H2: solo columnas nuevas e indice, sin CHECK ... IN (...).

ALTER TABLE electronic_invoices ADD COLUMN attempts INT NOT NULL DEFAULT 0;
ALTER TABLE electronic_invoices ADD COLUMN processing_started_at TIMESTAMP;
ALTER TABLE electronic_invoices ADD COLUMN next_retry_at TIMESTAMP;

CREATE INDEX IDX_electronic_invoices_status_retry ON electronic_invoices(status, next_retry_at);
