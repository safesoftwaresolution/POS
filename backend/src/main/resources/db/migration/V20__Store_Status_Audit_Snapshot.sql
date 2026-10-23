-- V20__Store_Status_Audit_Snapshot.sql
-- El SUPER_ADMIN puede eliminar definitivamente un local rechazado. La auditoria (V18) no debe perderse
-- con el: se suelta la FK a stores y cada fila guarda una copia (snapshot) de los datos del local para
-- seguir siendo legible cuando el local ya no exista. new_status admite 'DELETED' (sin CHECK, a proposito).
ALTER TABLE store_status_audit DROP CONSTRAINT FK_store_status_audit_store;

ALTER TABLE store_status_audit ADD COLUMN store_name   VARCHAR(100);
ALTER TABLE store_status_audit ADD COLUMN store_email  VARCHAR(100);
ALTER TABLE store_status_audit ADD COLUMN store_tax_id VARCHAR(50);

UPDATE store_status_audit
SET store_name   = (SELECT s.name   FROM stores s WHERE s.id = store_status_audit.store_id),
    store_email  = (SELECT s.email  FROM stores s WHERE s.id = store_status_audit.store_id),
    store_tax_id = (SELECT s.tax_id FROM stores s WHERE s.id = store_status_audit.store_id);
