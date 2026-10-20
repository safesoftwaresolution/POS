-- V16__Drop_Settings_Factus_Columns.sql
-- Las credenciales de Factus de 'settings' quedaron obsoletas desde V14, que las traslado a
-- store_billing_config (una fila por local, por entorno, con los secretos cifrados por la
-- aplicacion). Nada en el codigo lee 'settings' para facturar desde ese momento.
--
-- Antes de eliminarlas, se traslada a store_billing_config lo que SI se puede copiar tal cual
-- (client_id, username y el id de rango: no son secretos). client_secret y password NO se
-- copian: en store_billing_config van cifrados con AES-GCM aplicado en la capa Java
-- (SecretCryptoConverter), algo que esta migracion SQL no puede reproducir. Si algun local ya
-- tenia credenciales reales guardadas aqui, el SUPER_ADMIN debe volver a cargar el client_secret
-- y la contrasena desde PUT /api/v1/backoffice/stores/{storeId}/billing/credentials; el estado
-- queda en NOT_REQUESTED (nunca ACTIVE) hasta que eso pase, porque sin esos dos campos la
-- facturacion no puede activarse.
INSERT INTO store_billing_config (store_id, status, environment, sandbox_client_id, sandbox_username,
                                   sandbox_numbering_range_id, created_at, updated_at)
SELECT id, 'NOT_REQUESTED', 'SANDBOX', factus_client_id, factus_username, factus_numbering_range_id,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM settings
WHERE (factus_client_id IS NOT NULL OR factus_username IS NOT NULL OR factus_numbering_range_id IS NOT NULL)
  AND id NOT IN (SELECT store_id FROM store_billing_config);

ALTER TABLE settings DROP COLUMN factus_client_id;
ALTER TABLE settings DROP COLUMN factus_client_secret;
ALTER TABLE settings DROP COLUMN factus_username;
ALTER TABLE settings DROP COLUMN factus_password;
ALTER TABLE settings DROP COLUMN factus_numbering_range_id;
ALTER TABLE settings DROP COLUMN factus_enabled;
