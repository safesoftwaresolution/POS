-- V41__Retire_Factus_Leftovers.sql
-- Factus ya no es un proveedor soportado. Se borran las credenciales cifradas que guardaba store_billing_config
-- (client_id, client_secret, username y password de cada entorno): ningun codigo las lee y los secretos no deben
-- quedar en la base sin uso. El rango de numeracion activo de cada entorno y el company.id se conservan.
-- electronic_invoices.factus_number pasa a llamarse legal_number: es el numero legal de la factura, sea cual
-- sea el proveedor; los valores ya emitidos no cambian.
-- Seguro en H2: solo DROP COLUMN y RENAME COLUMN, sin CHECK ... IN (...).

ALTER TABLE store_billing_config DROP COLUMN sandbox_client_id;
ALTER TABLE store_billing_config DROP COLUMN sandbox_client_secret;
ALTER TABLE store_billing_config DROP COLUMN sandbox_username;
ALTER TABLE store_billing_config DROP COLUMN sandbox_password;
ALTER TABLE store_billing_config DROP COLUMN production_client_id;
ALTER TABLE store_billing_config DROP COLUMN production_client_secret;
ALTER TABLE store_billing_config DROP COLUMN production_username;
ALTER TABLE store_billing_config DROP COLUMN production_password;

ALTER TABLE electronic_invoices RENAME COLUMN factus_number TO legal_number;
