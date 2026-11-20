-- Facturacion con NIT compartido entre sucursales (businesses, U4).
-- Cada documento recuerda de que local tomo su empresa y sus rangos de numeracion cuando no son los suyos
-- (la sucursal principal del negocio). NULL = el propio local, que es lo que tienen todos los documentos
-- existentes. Se fija al reservar la emision y no cambia, igual que el resto del contexto fiscal (V43): un
-- documento historico no debe cambiar de empresa si despues cambia el negocio.
-- Sin FK a stores a proposito, igual que provider_company_id: es un dato historico del documento.
ALTER TABLE electronic_invoices ADD COLUMN billing_store_id BIGINT;
ALTER TABLE electronic_credit_notes ADD COLUMN billing_store_id BIGINT;
ALTER TABLE electronic_debit_notes ADD COLUMN billing_store_id BIGINT;
