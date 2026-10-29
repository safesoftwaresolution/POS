-- V30__Customer_Ownership_And_Audit.sql
-- Clientes con dueño: quién los creó y quién los editó por última vez (id para decidir permisos, nombre de usuario
-- como copia para mostrarlo sin consultar a users). Los clientes anteriores quedan con created_by NULL: solo un
-- administrador o supervisor puede editarlos.
ALTER TABLE customers ADD COLUMN created_by BIGINT;
ALTER TABLE customers ADD COLUMN created_by_username VARCHAR(50);
ALTER TABLE customers ADD COLUMN updated_by BIGINT;
ALTER TABLE customers ADD COLUMN updated_by_username VARCHAR(50);

-- Bitácora de auditoría genérica (módulo audit): una fila por cambio, de cualquier entidad. `changes` guarda en JSON
-- los campos modificados como {"campo": {"from": ..., "to": ...}}. Sin FK a users: la bitácora debe sobrevivir a
-- los cambios de usuarios, por eso guarda también el nombre de usuario.
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    user_id BIGINT,
    username VARCHAR(50),
    changes TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_audit_log_store FOREIGN KEY (store_id) REFERENCES stores(id)
);
CREATE INDEX IDX_audit_log_entity ON audit_log(store_id, entity_type, entity_id, created_at);
