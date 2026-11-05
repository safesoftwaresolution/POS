-- V35__Note_Emission_Lease.sql
-- Las notas credito y debito siguen la misma disciplina que las facturas (V34): la llamada al proveedor sale de la
-- transaccion y la emision se reserva con un lease en la fila (processing_started_at); attempts cuenta los intentos.
-- Las notas no tienen reintento automatico, por eso no llevan next_retry_at.
-- Seguro en H2: solo columnas nuevas, sin CHECK ... IN (...).

ALTER TABLE electronic_credit_notes ADD COLUMN attempts INT NOT NULL DEFAULT 0;
ALTER TABLE electronic_credit_notes ADD COLUMN processing_started_at TIMESTAMP;

ALTER TABLE electronic_debit_notes ADD COLUMN attempts INT NOT NULL DEFAULT 0;
ALTER TABLE electronic_debit_notes ADD COLUMN processing_started_at TIMESTAMP;
