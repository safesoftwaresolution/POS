-- V14__Store_Billing_Config.sql
-- Configuracion de facturacion electronica por local (1 a 1 con stores).
-- Guarda el estado del proceso de habilitacion y las credenciales que Factus entrega
-- para cada local: un juego para el entorno de pruebas (sandbox) y otro para produccion.
-- client_secret y password se guardan CIFRADOS por la aplicacion (AES-GCM), nunca en claro.
-- Las columnas factus_* de 'settings' se mantienen por ahora; se retiran en una migracion posterior.

CREATE TABLE store_billing_config (
    store_id                          BIGINT       NOT NULL,
    status                            VARCHAR(30)  NOT NULL DEFAULT 'NOT_REQUESTED',
    status_reason                     VARCHAR(500),
    environment                       VARCHAR(12)  NOT NULL DEFAULT 'SANDBOX',

    sandbox_client_id                 VARCHAR(255),
    sandbox_client_secret             VARCHAR(1024),
    sandbox_username                  VARCHAR(255),
    sandbox_password                  VARCHAR(1024),
    sandbox_numbering_range_id        INT,

    production_client_id              VARCHAR(255),
    production_client_secret          VARCHAR(1024),
    production_username               VARCHAR(255),
    production_password               VARCHAR(1024),
    production_numbering_range_id     INT,

    updated_by                        VARCHAR(100),
    created_at                        TIMESTAMP    NOT NULL,
    updated_at                        TIMESTAMP    NOT NULL,

    CONSTRAINT PK_store_billing_config PRIMARY KEY (store_id),
    CONSTRAINT FK_store_billing_config_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT CK_store_billing_config_status CHECK (status IN
        ('NOT_REQUESTED', 'DOCUMENTS_SUBMITTED', 'AWAITING_PROVIDER', 'ACTIVE', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT CK_store_billing_config_environment CHECK (environment IN ('SANDBOX', 'PRODUCTION'))
);

CREATE INDEX IDX_store_billing_config_status ON store_billing_config(status);
