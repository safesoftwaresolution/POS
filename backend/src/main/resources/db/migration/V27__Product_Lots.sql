-- V27__Product_Lots.sql
-- Lotes ligeros de vencimiento. Cada entrada de stock puede guardar un lote (cantidad + vencimiento + código opcional).
-- No se descuenta stock por lote: el stock del producto (products.quantity_available) sigue siendo el único contador y
-- lo que queda de cada lote se deriva asumiendo que se vende primero lo que vence primero (ver modules/products/spec.md).
-- Un producto sin lotes no vence. Sin CHECK ... IN para que sea segura en H2.
CREATE TABLE product_lots (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    lot_code VARCHAR(50),
    quantity INT NOT NULL,
    expiration_date DATE NOT NULL,
    received_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_product_lots_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT FK_product_lots_product FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE INDEX IDX_product_lots_product ON product_lots(product_id);
CREATE INDEX IDX_product_lots_store_expiration ON product_lots(store_id, expiration_date);
