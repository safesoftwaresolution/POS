-- V9__Store_Scoping_Isolation.sql
-- Cierra el aislamiento multi-tenant: sales/purchases/inventory_movements nunca tuvieron
-- store_id, y products/customers/suppliers/settings tenian la columna (agregada en V3) pero
-- ningun servicio la leia ni la escribia. En la practica, todos los locales compartian
-- catalogo, ventas, compras, movimientos de inventario y datos del negocio entre si.

-- 1. Ventas: se hereda el local del usuario que la registro (vendedor/cajero pertenece a un
--    unico local). Cualquier registro huerfano (usuario sin store_id) cae al local por
--    defecto sembrado en V3, para no dejar filas sin dueno.
ALTER TABLE sales ADD COLUMN IF NOT EXISTS store_id BIGINT;
UPDATE sales s SET store_id = (SELECT u.store_id FROM users u WHERE u.id = s.user_id) WHERE s.store_id IS NULL;
UPDATE sales SET store_id = (SELECT id FROM stores WHERE email = 'default@platform.internal') WHERE store_id IS NULL;
ALTER TABLE sales ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE sales ADD CONSTRAINT FK_sales_store FOREIGN KEY (store_id) REFERENCES stores(id);
CREATE INDEX IF NOT EXISTS IDX_sales_store ON sales(store_id);

-- 2. Compras: mismo criterio (local del usuario que la registro).
ALTER TABLE purchases ADD COLUMN IF NOT EXISTS store_id BIGINT;
UPDATE purchases p SET store_id = (SELECT u.store_id FROM users u WHERE u.id = p.user_id) WHERE p.store_id IS NULL;
UPDATE purchases SET store_id = (SELECT id FROM stores WHERE email = 'default@platform.internal') WHERE store_id IS NULL;
ALTER TABLE purchases ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE purchases ADD CONSTRAINT FK_purchases_store FOREIGN KEY (store_id) REFERENCES stores(id);
CREATE INDEX IF NOT EXISTS IDX_purchases_store ON purchases(store_id);

-- 3. Movimientos de inventario: mismo criterio.
ALTER TABLE inventory_movements ADD COLUMN IF NOT EXISTS store_id BIGINT;
UPDATE inventory_movements m SET store_id = (SELECT u.store_id FROM users u WHERE u.id = m.user_id) WHERE m.store_id IS NULL;
UPDATE inventory_movements SET store_id = (SELECT id FROM stores WHERE email = 'default@platform.internal') WHERE store_id IS NULL;
ALTER TABLE inventory_movements ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE inventory_movements ADD CONSTRAINT FK_movements_store FOREIGN KEY (store_id) REFERENCES stores(id);
CREATE INDEX IF NOT EXISTS IDX_movements_store ON inventory_movements(store_id);

-- 4. products/customers/suppliers: la columna store_id ya existia desde V3 pero quedaba NULL
--    en todo registro creado despues de esa migracion (ningun servicio la completaba). Se
--    reasigna como mejor esfuerzo al local por defecto y se endurece a NOT NULL.
UPDATE products  SET store_id = (SELECT id FROM stores WHERE email = 'default@platform.internal') WHERE store_id IS NULL;
UPDATE customers SET store_id = (SELECT id FROM stores WHERE email = 'default@platform.internal') WHERE store_id IS NULL;
UPDATE suppliers SET store_id = (SELECT id FROM stores WHERE email = 'default@platform.internal') WHERE store_id IS NULL;
ALTER TABLE products  ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE customers ALTER COLUMN store_id SET NOT NULL;
ALTER TABLE suppliers ALTER COLUMN store_id SET NOT NULL;

-- 5. Codigo interno / codigo de barras / identificacion / NIT eran unicos de forma GLOBAL:
--    dos locales distintos no podian usar el mismo codigo de barras de fabrica (comun, ej.
--    un mismo producto de una misma marca) ni el mismo cliente/proveedor generico. Se
--    reemplaza por unicidad por local.
ALTER TABLE products DROP CONSTRAINT IF EXISTS UK_products_internal_code;
ALTER TABLE products DROP CONSTRAINT IF EXISTS UK_products_barcode;
ALTER TABLE products ADD CONSTRAINT UK_products_store_internal_code UNIQUE (store_id, internal_code);
ALTER TABLE products ADD CONSTRAINT UK_products_store_barcode UNIQUE (store_id, barcode);

ALTER TABLE customers DROP CONSTRAINT IF EXISTS UK_customers_identification;
ALTER TABLE customers ADD CONSTRAINT UK_customers_store_identification UNIQUE (store_id, identification);

ALTER TABLE suppliers DROP CONSTRAINT IF EXISTS UK_suppliers_tax_id;
ALTER TABLE suppliers ADD CONSTRAINT UK_suppliers_store_tax_id UNIQUE (store_id, tax_id);

-- 6. Settings era un singleton global (id fijo = 1): todos los locales compartian nombre,
--    direccion, telefono, NIT y logo del negocio impresos en el ticket. Se convierte en una
--    fila por local, usando el id del local como llave primaria (relacion 1 a 1 con stores)
--    en lugar de la columna store_id separada.
ALTER TABLE settings ALTER COLUMN id TYPE BIGINT;
UPDATE settings SET id = store_id WHERE store_id IS NOT NULL;
ALTER TABLE settings DROP COLUMN IF EXISTS store_id;
ALTER TABLE settings ADD CONSTRAINT FK_settings_store FOREIGN KEY (id) REFERENCES stores(id);
