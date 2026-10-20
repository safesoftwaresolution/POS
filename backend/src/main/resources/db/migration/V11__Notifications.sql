-- V11__Notifications.sql
-- Notificaciones en-app por local (compartidas por todo el equipo del local, no por usuario
-- individual): alertas de stock bajo y de revision de documentos KYC, entre otras futuras.

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(150) NOT NULL,
    message VARCHAR(500) NOT NULL,
    link_path VARCHAR(255),
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_notifications_store FOREIGN KEY (store_id) REFERENCES stores(id)
);

CREATE INDEX IF NOT EXISTS IDX_notifications_store ON notifications(store_id);
