-- V10__Sale_Returns.sql
-- Devoluciones de venta: la venta original NUNCA se edita (sigue siendo el comprobante
-- fiscal inmutable). Una devolucion es un registro aparte, ligado a la venta y a los items
-- puntuales que se devuelven, que repone el stock y calcula el monto a reembolsar.

CREATE TABLE IF NOT EXISTS sale_returns (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reason VARCHAR(255),
    total_refund DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_sale_returns_sale FOREIGN KEY (sale_id) REFERENCES sales(id),
    CONSTRAINT FK_sale_returns_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT FK_sale_returns_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS IDX_sale_returns_sale ON sale_returns(sale_id);
CREATE INDEX IF NOT EXISTS IDX_sale_returns_store ON sale_returns(store_id);

-- Item devuelto: referencia el sale_item exacto de la venta original (no el producto
-- directamente), para poder validar cuanto de ESA linea especifica ya se devolvio antes,
-- incluso si el mismo producto aparece en mas de una linea de la misma venta.
CREATE TABLE IF NOT EXISTS sale_return_items (
    id BIGSERIAL PRIMARY KEY,
    sale_return_id BIGINT NOT NULL,
    sale_item_id BIGINT NOT NULL,
    -- Copia de sale_items.product_id al momento de la devolucion: evita un join extra al
    -- listar devoluciones y sigue siendo correcta aunque el producto cambie despues.
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    CONSTRAINT FK_sale_return_items_return FOREIGN KEY (sale_return_id) REFERENCES sale_returns(id) ON DELETE CASCADE,
    CONSTRAINT FK_sale_return_items_sale_item FOREIGN KEY (sale_item_id) REFERENCES sale_items(id),
    CONSTRAINT FK_sale_return_items_product FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE INDEX IF NOT EXISTS IDX_sale_return_items_sale_item ON sale_return_items(sale_item_id);
