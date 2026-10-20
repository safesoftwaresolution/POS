-- V6__Dynamic_Parameter_Catalogs.sql
-- Motor dinámico de parámetros / catálogos: temas (parameter_topics) y sus valores
-- (parameter_values). Permite definir catálogos configurables (métodos de pago, motivos
-- de devolución, zonas de entrega, etc.) sin desplegar código.
-- NOTA: la especificación pedía V4, pero V4 (Refresh_Tokens) y V5 ya están aplicadas;
-- se usa la siguiente versión libre (V6) para no romper el historial de Flyway.

-- 1. Encabezado de temas
CREATE TABLE IF NOT EXISTS parameter_topics (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 2. Valores por tema
CREATE TABLE IF NOT EXISTS parameter_values (
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    label VARCHAR(100) NOT NULL,
    extra_value VARCHAR(255),
    sort_order INT DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_param_value_topic FOREIGN KEY (topic_id) REFERENCES parameter_topics(id) ON DELETE CASCADE,
    CONSTRAINT UQ_param_value_topic_code UNIQUE (topic_id, code)
);

-- 3. Índices de rendimiento
CREATE INDEX IF NOT EXISTS IDX_param_values_topic  ON parameter_values(topic_id);
CREATE INDEX IF NOT EXISTS IDX_param_values_active ON parameter_values(topic_id, active);

-- 4. Datos semilla: temas base del sistema (is_system = TRUE)
INSERT INTO parameter_topics (code, name, description, is_system, created_at, updated_at) VALUES
    ('PAYMENT_METHODS', 'Métodos de Pago',        'Formas de pago aceptadas en el punto de venta', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('RETURN_REASONS',  'Motivos de Devolución',  'Razones por las que se acepta una devolución',  TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('DELIVERY_ZONES',  'Zonas de Entrega',       'Zonas de despacho y su costo asociado',         TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 4.1 Valores de PAYMENT_METHODS (extra_value = código de medio de pago DIAN)
INSERT INTO parameter_values (topic_id, code, label, extra_value, sort_order, active, created_at, updated_at) VALUES
    ((SELECT id FROM parameter_topics WHERE code = 'PAYMENT_METHODS'), 'CASH',     'Efectivo',                '10', 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM parameter_topics WHERE code = 'PAYMENT_METHODS'), 'NEQUI',    'Nequi / Daviplata',       '42', 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM parameter_topics WHERE code = 'PAYMENT_METHODS'), 'CARD',     'Tarjeta Débito/Crédito',  '48', 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM parameter_topics WHERE code = 'PAYMENT_METHODS'), 'TRANSFER', 'Transferencia Bancaria',  '31', 4, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 4.2 Valores de RETURN_REASONS
INSERT INTO parameter_values (topic_id, code, label, extra_value, sort_order, active, created_at, updated_at) VALUES
    ((SELECT id FROM parameter_topics WHERE code = 'RETURN_REASONS'), 'DAMAGED_PRODUCT',        'Producto Dañado',       NULL, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM parameter_topics WHERE code = 'RETURN_REASONS'), 'WRONG_ITEM',             'Producto Equivocado',   NULL, 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM parameter_topics WHERE code = 'RETURN_REASONS'), 'EXPIRED',                'Producto Vencido',      NULL, 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM parameter_topics WHERE code = 'RETURN_REASONS'), 'CUSTOMER_DISSATISFIED',  'Cliente Insatisfecho',  NULL, 4, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 4.3 Valores de DELIVERY_ZONES (extra_value = costo de envío en COP)
INSERT INTO parameter_values (topic_id, code, label, extra_value, sort_order, active, created_at, updated_at) VALUES
    ((SELECT id FROM parameter_topics WHERE code = 'DELIVERY_ZONES'), 'LOCAL',    'Zona Local (Ciudad)',  '0',     1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM parameter_topics WHERE code = 'DELIVERY_ZONES'), 'METRO',    'Área Metropolitana',   '5000',  2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ((SELECT id FROM parameter_topics WHERE code = 'DELIVERY_ZONES'), 'NATIONAL', 'Nivel Nacional',       '15000', 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
