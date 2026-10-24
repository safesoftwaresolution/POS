CREATE TABLE IF NOT EXISTS electronic_debit_notes (
    id BIGSERIAL PRIMARY KEY, 
    store_id BIGINT NOT NULL,
    sale_id BIGINT,
    reference_code VARCHAR(100) NOT NULL UNIQUE,
    correction_concept_code VARCHAR(10) NOT NULL,
    customization_id VARCHAR(10) NOT NULL DEFAULT '30',
    bill_number VARCHAR(50) NOT NULL,
    debit_note_number VARCHAR(50),
    cude VARCHAR(255), 
    qr_code TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    pdf_url VARCHAR(500),
    validated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_electronic_debit_notes_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT FK_electronic_debit_notes_sale FOREIGN KEY (sale_id) REFERENCES sales(id)
);

CREATE INDEX IF NOT EXISTS IDX_electronic_debit_notes_store ON electronic_debit_notes(store_id);
CREATE INDEX IF NOT EXISTS IDX_electronic_debit_notes_store_status ON electronic_debit_notes(store_id, status);
CREATE INDEX IF NOT EXISTS IDX_electronic_debit_notes_bill ON electronic_debit_notes(bill_number);