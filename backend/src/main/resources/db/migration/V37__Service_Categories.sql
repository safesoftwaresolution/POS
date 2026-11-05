-- Service categories are their own global catalog, separate from product categories. Each one is offered to the store
-- categories linked in service_category_store_categories; a category with no link is offered to every store.
-- No CHECK ... IN, so it is safe on H2.
CREATE TABLE service_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT UK_service_categories_name UNIQUE (name)
);

CREATE TABLE service_category_store_categories (
    service_category_id BIGINT NOT NULL,
    store_category_id BIGINT NOT NULL,
    PRIMARY KEY (service_category_id, store_category_id),
    CONSTRAINT FK_scsc_service_category FOREIGN KEY (service_category_id) REFERENCES service_categories(id),
    CONSTRAINT FK_scsc_store_category FOREIGN KEY (store_category_id) REFERENCES store_categories(id)
);

-- Existing services keep working: the product categories they use become service categories with the same name,
-- offered to the store categories of the stores that already use them.
INSERT INTO service_categories (name, description, active, created_at, updated_at)
SELECT c.name, c.description, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM categories c
WHERE c.id IN (SELECT DISTINCT category_id FROM services);

INSERT INTO service_category_store_categories (service_category_id, store_category_id)
SELECT DISTINCT sc.id, st.store_category_id
FROM services s
JOIN categories c ON c.id = s.category_id
JOIN service_categories sc ON sc.name = c.name
JOIN stores st ON st.id = s.store_id
WHERE st.store_category_id IS NOT NULL;

ALTER TABLE services DROP CONSTRAINT FK_services_category;

UPDATE services
SET category_id = (SELECT sc.id FROM service_categories sc JOIN categories c ON c.name = sc.name WHERE c.id = services.category_id);

ALTER TABLE services ADD CONSTRAINT FK_services_service_category FOREIGN KEY (category_id) REFERENCES service_categories(id);
