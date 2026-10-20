-- V3__Multitenancy_And_Backoffice.sql
-- Adds multi-tenancy support (store_id) and back office tables

-- =========================================================
-- BACK OFFICE: NUEVAS TABLAS
-- =========================================================

-- 1. store_categories: categorias de clasificacion de locales (SUPER_ADMIN only)
CREATE TABLE IF NOT EXISTS store_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 2. stores: los tenants del SaaS
CREATE TABLE IF NOT EXISTS stores (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    store_category_id BIGINT,
    phone VARCHAR(20),
    email VARCHAR(100) NOT NULL UNIQUE,
    website VARCHAR(255),
    address VARCHAR(255),
    tax_id VARCHAR(50),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_stores_category FOREIGN KEY (store_category_id) REFERENCES store_categories(id),
    CONSTRAINT CK_stores_status CHECK (status IN ('ACTIVE','INACTIVE','PENDING_VERIFICATION','SUSPENDED'))
);

-- 3. store_documents: documentos KYC enviados por cada local
CREATE TABLE IF NOT EXISTS store_documents (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    document_url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(255),
    uploaded_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    CONSTRAINT FK_store_docs_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT CK_store_docs_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT CK_store_docs_type CHECK (document_type IN ('RUT','COMMERCE_CHAMBER','ID_CARD','BANK_CERTIFICATE','OTHER'))
);

-- 4. legal_documents: textos legales gestionados por SUPER_ADMIN
CREATE TABLE IF NOT EXISTS legal_documents (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    version VARCHAR(20) NOT NULL DEFAULT '1.0',
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

-- 5. support_tickets: PQRs y reportes de bugs del sistema
CREATE TABLE IF NOT EXISTS support_tickets (
    id BIGSERIAL PRIMARY KEY,
    ticket_number VARCHAR(30) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(15) NOT NULL DEFAULT 'OPEN',
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    contact_name VARCHAR(100) NOT NULL,
    contact_email VARCHAR(100) NOT NULL,
    contact_phone VARCHAR(20),
    store_id BIGINT,
    system_info TEXT,
    resolution_notes TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,
    CONSTRAINT FK_tickets_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT CK_tickets_type CHECK (type IN ('PETITION','COMPLAINT','CLAIM','SUGGESTION','BUG_REPORT')),
    CONSTRAINT CK_tickets_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT CK_tickets_status CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED'))
);

-- =========================================================
-- MULTI-TENANCY: Store por defecto para datos existentes
-- =========================================================
INSERT INTO stores (name, email, status, email_verified, created_at, updated_at)
VALUES ('Default Store', 'default@platform.internal', 'ACTIVE', TRUE, NOW(), NOW());

-- =========================================================
-- MULTI-TENANCY: Agregar store_id a tablas del POS
-- =========================================================
ALTER TABLE users     ADD COLUMN IF NOT EXISTS store_id BIGINT;
ALTER TABLE products  ADD COLUMN IF NOT EXISTS store_id BIGINT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS store_id BIGINT;
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS store_id BIGINT;
ALTER TABLE settings  ADD COLUMN IF NOT EXISTS store_id BIGINT;

-- Asignar registros existentes al store por defecto
UPDATE users     SET store_id = (SELECT id FROM stores WHERE email = 'default@platform.internal') WHERE store_id IS NULL;
UPDATE products  SET store_id = (SELECT id FROM stores WHERE email = 'default@platform.internal') WHERE store_id IS NULL;
UPDATE customers SET store_id = (SELECT id FROM stores WHERE email = 'default@platform.internal') WHERE store_id IS NULL;
UPDATE suppliers SET store_id = (SELECT id FROM stores WHERE email = 'default@platform.internal') WHERE store_id IS NULL;
UPDATE settings  SET store_id = (SELECT id FROM stores WHERE email = 'default@platform.internal') WHERE store_id IS NULL;

-- Agregar FK despues de poblar
ALTER TABLE users     ADD CONSTRAINT FK_users_store     FOREIGN KEY (store_id) REFERENCES stores(id);
ALTER TABLE products  ADD CONSTRAINT FK_products_store  FOREIGN KEY (store_id) REFERENCES stores(id);
ALTER TABLE customers ADD CONSTRAINT FK_customers_store FOREIGN KEY (store_id) REFERENCES stores(id);
ALTER TABLE suppliers ADD CONSTRAINT FK_suppliers_store FOREIGN KEY (store_id) REFERENCES stores(id);
ALTER TABLE settings  ADD CONSTRAINT FK_settings_store  FOREIGN KEY (store_id) REFERENCES stores(id);

-- =========================================================
-- INDICES DE RENDIMIENTO
-- =========================================================
CREATE INDEX IF NOT EXISTS IDX_users_store           ON users(store_id);
CREATE INDEX IF NOT EXISTS IDX_products_store        ON products(store_id);
CREATE INDEX IF NOT EXISTS IDX_customers_store       ON customers(store_id);
CREATE INDEX IF NOT EXISTS IDX_suppliers_store       ON suppliers(store_id);
CREATE INDEX IF NOT EXISTS IDX_settings_store        ON settings(store_id);
CREATE INDEX IF NOT EXISTS IDX_stores_status         ON stores(status);
CREATE INDEX IF NOT EXISTS IDX_stores_email_verified ON stores(email_verified);
CREATE INDEX IF NOT EXISTS IDX_legal_slug            ON legal_documents(slug);
CREATE INDEX IF NOT EXISTS IDX_legal_published       ON legal_documents(slug, published);
CREATE INDEX IF NOT EXISTS IDX_tickets_type_status   ON support_tickets(type, status);
CREATE INDEX IF NOT EXISTS IDX_tickets_number        ON support_tickets(ticket_number);
