-- A store runs with several templates: the ones of its category (store_category_templates) plus extras added to that
-- store by the SUPER_ADMIN (store_templates). The effective set is their union (RETAIL when both are empty).
-- Expand/contract: store_categories.template (V33) is copied and then left unused and unmapped so a rollback of the app
-- keeps working; a later migration drops it.
-- No CHECK on the template values: the StoreTemplate enum validates them
-- (H2 2.4.x mis-validates CHECK ... IN (...) from another connection).
CREATE TABLE store_category_templates (
    category_id BIGINT NOT NULL,
    template VARCHAR(30) NOT NULL,
    PRIMARY KEY (category_id, template),
    CONSTRAINT FK_store_category_templates_category FOREIGN KEY (category_id) REFERENCES store_categories(id)
);

INSERT INTO store_category_templates (category_id, template) SELECT id, template FROM store_categories;

-- A clothing boutique sells products and takes appointments.
INSERT INTO store_category_templates (category_id, template)
SELECT id, 'RETAIL' FROM store_categories
WHERE name = 'Boutique'
  AND NOT EXISTS (SELECT 1 FROM store_category_templates t WHERE t.category_id = store_categories.id AND t.template = 'RETAIL');

CREATE TABLE store_templates (
    store_id BIGINT NOT NULL,
    template VARCHAR(30) NOT NULL,
    PRIMARY KEY (store_id, template),
    CONSTRAINT FK_store_templates_store FOREIGN KEY (store_id) REFERENCES stores(id)
);
