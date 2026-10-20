-- V11__Product_Subcategories.sql
-- Subcategorias: a diferencia de las categorias (globales, solo SUPER_ADMIN), las
-- subcategorias son por tienda -- cada tienda crea las suyas propias bajo una categoria
-- global existente, sin necesidad de pedirselo a la plataforma. Administrator y Supervisor
-- las gestionan; Seller solo las consulta (igual que el resto del catalogo).
CREATE TABLE IF NOT EXISTS product_subcategories (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_subcategories_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT FK_subcategories_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT UK_subcategories_store_category_name UNIQUE (store_id, category_id, name)
);
CREATE INDEX IF NOT EXISTS IDX_subcategories_store ON product_subcategories(store_id);
CREATE INDEX IF NOT EXISTS IDX_subcategories_category ON product_subcategories(category_id);

-- Un producto puede opcionalmente afinar su clasificacion con una subcategoria de su
-- propia tienda; sigue siendo obligatorio elegir la categoria global (category_id).
ALTER TABLE products ADD COLUMN IF NOT EXISTS subcategory_id BIGINT;
ALTER TABLE products ADD CONSTRAINT FK_products_subcategory FOREIGN KEY (subcategory_id) REFERENCES product_subcategories(id);
