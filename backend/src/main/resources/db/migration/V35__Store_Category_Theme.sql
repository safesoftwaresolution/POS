-- Each store category carries the visual theme its stores run with. The store's theme is derived from its
-- category (like the template, V33); categories created later by the SUPER_ADMIN keep DEFAULT unless they pick one.
-- No CHECK constraint on purpose: the allowed values are validated by the StoreTheme enum
-- (H2 2.4.x mis-validates CHECK ... IN (...) from another connection).
ALTER TABLE store_categories ADD COLUMN theme VARCHAR(30) NOT NULL DEFAULT 'DEFAULT';

UPDATE store_categories SET theme = 'GROCERY' WHERE name IN ('Tienda de barrio / Minimercado', 'Supermercado');
UPDATE store_categories SET theme = 'PHARMACY' WHERE name = 'Droguería / Farmacia';
UPDATE store_categories SET theme = 'BAKERY' WHERE name = 'Panadería';
UPDATE store_categories SET theme = 'STATIONERY' WHERE name = 'Papelería';
UPDATE store_categories SET theme = 'HARDWARE' WHERE name = 'Ferretería';
UPDATE store_categories SET theme = 'FOOD' WHERE name = 'Restaurante / Cafetería';
UPDATE store_categories SET theme = 'LIQUOR' WHERE name = 'Licorera';
UPDATE store_categories SET theme = 'PETS' WHERE name = 'Tienda de mascotas';
UPDATE store_categories SET theme = 'BEAUTY' WHERE name IN ('Barbería', 'Peluquería', 'Salón de uñas y pies');
UPDATE store_categories SET theme = 'FASHION' WHERE name = 'Boutique';
