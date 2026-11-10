-- Appointment calendar for stores with the APPOINTMENTS template. Customer and service names are copied
-- at booking time so editing the catalog never rewrites the agenda. No CHECK on status: the enum validates it
-- (H2 2.4.x mis-validates CHECK ... IN (...) from another connection).
CREATE TABLE appointments (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(150) NOT NULL,
    service_id BIGINT NOT NULL,
    service_name VARCHAR(150) NOT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    notes VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_appointments_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT FK_appointments_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT FK_appointments_service FOREIGN KEY (service_id) REFERENCES services(id),
    CONSTRAINT CK_appointments_range CHECK (end_at > start_at)
);

CREATE INDEX IDX_appointments_store_start ON appointments(store_id, start_at);
