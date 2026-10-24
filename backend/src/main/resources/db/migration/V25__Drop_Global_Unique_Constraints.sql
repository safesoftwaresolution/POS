-- V25__Drop_Global_Unique_Constraints.sql
-- V9 pretendia pasar la unicidad de codigo interno / codigo de barras / identificacion / NIT de
-- global a por local, pero hacia DROP CONSTRAINT IF EXISTS con nombres inventados (UK_*): los
-- UNIQUE en linea de V1 no tenian nombre, asi que PostgreSQL les puso el suyo (<tabla>_<columna>_key)
-- y los DROP no hicieron nada. En produccion la unicidad global siguio vigente: el segundo local no
-- podia crear su cliente "Consumidor final" (9999999999) ni repetir un codigo de producto. Se
-- eliminan aqui los nombres reales; la unicidad por local (UK_*_store_*) ya existe desde V9.
-- Es idempotente y no hace nada en bases donde esos nombres no existen (p. ej. H2).
ALTER TABLE products  DROP CONSTRAINT IF EXISTS products_internal_code_key;
ALTER TABLE products  DROP CONSTRAINT IF EXISTS products_barcode_key;
ALTER TABLE customers DROP CONSTRAINT IF EXISTS customers_identification_key;
ALTER TABLE suppliers DROP CONSTRAINT IF EXISTS suppliers_tax_id_key;
