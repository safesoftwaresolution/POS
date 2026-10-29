-- V29__Services_Catalog.sql
-- Catálogo de servicios (spa, peluquería, consultoría…): se cobran y se facturan como un producto, pero no tienen
-- stock, lotes ni movimientos de inventario. Una línea de venta apunta a un producto O a un servicio (item_type).
-- Sin CHECK ... IN para que sea segura en H2.
CREATE TABLE services (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    category_id BIGINT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    tax_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
    unspsc_code VARCHAR(50),
    unit_measure_code VARCHAR(10) NOT NULL DEFAULT '94',
    duration_minutes INT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_services_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT FK_services_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT UK_services_store_code UNIQUE (store_id, code)
);
CREATE INDEX IDX_services_store_active ON services(store_id, active);

-- Líneas de venta: producto o servicio. Las ventas existentes quedan como PRODUCT.
ALTER TABLE sale_items ALTER COLUMN product_id DROP NOT NULL;
ALTER TABLE sale_items ADD COLUMN item_type VARCHAR(10) NOT NULL DEFAULT 'PRODUCT';
ALTER TABLE sale_items ADD COLUMN service_id BIGINT;
-- Copia de lo facturado al momento de la venta: editar o desactivar un servicio no altera facturas ya emitidas.
ALTER TABLE sale_items ADD COLUMN item_name VARCHAR(150);
ALTER TABLE sale_items ADD COLUMN unit_measure_code VARCHAR(10);
ALTER TABLE sale_items ADD COLUMN unspsc_code VARCHAR(50);
ALTER TABLE sale_items ADD CONSTRAINT FK_sale_items_service FOREIGN KEY (service_id) REFERENCES services(id);
ALTER TABLE sale_items ADD CONSTRAINT CK_sale_items_target CHECK (
    (item_type = 'PRODUCT' AND product_id IS NOT NULL AND service_id IS NULL)
    OR (item_type = 'SERVICE' AND service_id IS NOT NULL AND product_id IS NULL)
);
CREATE INDEX IDX_sale_items_service ON sale_items(service_id);

-- Devoluciones: la línea devuelta puede ser un servicio (solo reembolsa, no repone stock).
ALTER TABLE sale_return_items ALTER COLUMN product_id DROP NOT NULL;
ALTER TABLE sale_return_items ADD COLUMN service_id BIGINT;
ALTER TABLE sale_return_items ADD CONSTRAINT FK_sale_return_items_service FOREIGN KEY (service_id) REFERENCES services(id);
