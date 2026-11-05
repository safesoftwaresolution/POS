-- Each store category carries the functional template its stores run with (RETAIL, FOOD, APPOINTMENTS).
-- The store's template is derived from its category; the SUPER_ADMIN edits it per category.
-- No CHECK constraint on purpose: the allowed values are validated by the StoreTemplate enum
-- (H2 2.4.x mis-validates CHECK ... IN (...) from another connection).
ALTER TABLE store_categories ADD COLUMN template VARCHAR(30) NOT NULL DEFAULT 'RETAIL';

UPDATE store_categories SET template = 'FOOD' WHERE name = 'Restaurante / Cafetería';
