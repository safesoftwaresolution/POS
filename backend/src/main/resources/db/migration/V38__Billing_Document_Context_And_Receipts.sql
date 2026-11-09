-- Forward-only hardening for the V37 billing branch history.
-- Unknown historical context/receipts deliberately remain NULL: current store configuration
-- and the legacy completed flag are not evidence of the original fiscal company or outcome.
ALTER TABLE electronic_invoices ADD COLUMN provider_name VARCHAR(32);
ALTER TABLE electronic_invoices ADD COLUMN provider_company_id VARCHAR(64);

ALTER TABLE electronic_credit_notes ADD COLUMN environment VARCHAR(12);
ALTER TABLE electronic_credit_notes ADD COLUMN provider_name VARCHAR(32);
ALTER TABLE electronic_credit_notes ADD COLUMN provider_company_id VARCHAR(64);

ALTER TABLE electronic_debit_notes ADD COLUMN environment VARCHAR(12);
ALTER TABLE electronic_debit_notes ADD COLUMN provider_name VARCHAR(32);
ALTER TABLE electronic_debit_notes ADD COLUMN provider_company_id VARCHAR(64);

ALTER TABLE billing_document_number ADD COLUMN outcome VARCHAR(24);
ALTER TABLE billing_document_number ADD COLUMN provider_document_id VARCHAR(100);
ALTER TABLE billing_document_number ADD COLUMN request_payload TEXT;
ALTER TABLE billing_document_number ADD COLUMN response_payload TEXT;
ALTER TABLE billing_document_number ADD COLUMN request_hash VARCHAR(64);
