-- V37__Billing_Self_Service.sql
-- La habilitacion de la facturacion electronica pasa a autoservicio con Alegra, que no pide documentos.
-- 1. Los estados de store_billing_config quedan NOT_REQUESTED -> IN_SETUP -> ACTIVE <-> SUSPENDED. Se retira el
--    CHECK de estados (se validan en la aplicacion) y los estados retirados (DOCUMENTS_SUBMITTED,
--    AWAITING_PROVIDER, REJECTED) pasan a IN_SETUP: esos locales siguen su habilitacion desde el asistente.
-- 2. billing_company_profile guarda los datos fiscales del local (los del RUT) con los que se crea su empresa
--    en el proveedor. Uno por local.
-- 3. electronic_invoices.environment guarda con que entorno (SANDBOX o PRODUCTION) se emitio cada factura. Las
--    existentes toman el entorno actual de su local; las de un local sin configuracion quedan en null.
-- Seguro en H2: sin CHECK ... IN (...); los valores de status y environment se validan en la aplicacion.
-- IF EXISTS: algunas pruebas de migracion sueltan ese CHECK a mano por un bug de H2 2.4.x antes de llegar aqui.

ALTER TABLE store_billing_config DROP CONSTRAINT IF EXISTS CK_store_billing_config_status;

UPDATE store_billing_config
SET status = 'IN_SETUP'
WHERE status IN ('DOCUMENTS_SUBMITTED', 'AWAITING_PROVIDER', 'REJECTED');

CREATE TABLE billing_company_profile (
    store_id             BIGINT       NOT NULL,
    legal_name           VARCHAR(450) NOT NULL,
    trade_name           VARCHAR(450),
    nit                  VARCHAR(30)  NOT NULL,
    dv                   VARCHAR(1)   NOT NULL,
    identification_type  VARCHAR(2)   NOT NULL DEFAULT '31',
    organization_type    INT          NOT NULL,
    regime_code          VARCHAR(200) NOT NULL,
    economic_activity    VARCHAR(4),
    email                VARCHAR(255) NOT NULL,
    phone                VARCHAR(30),
    address              VARCHAR(255) NOT NULL,
    city_code            VARCHAR(5)   NOT NULL,
    department_code      VARCHAR(2)   NOT NULL,
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP    NOT NULL,

    CONSTRAINT PK_billing_company_profile PRIMARY KEY (store_id),
    CONSTRAINT FK_billing_company_profile_store FOREIGN KEY (store_id) REFERENCES stores(id)
);

ALTER TABLE electronic_invoices ADD COLUMN environment VARCHAR(12);

UPDATE electronic_invoices
SET environment = (
    SELECT c.environment
    FROM store_billing_config c
    WHERE c.store_id = electronic_invoices.store_id
)
WHERE environment IS NULL;
