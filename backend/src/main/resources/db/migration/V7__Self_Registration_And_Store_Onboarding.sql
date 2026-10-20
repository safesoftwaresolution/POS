-- V7__Self_Registration_And_Store_Onboarding.sql
-- Auto-registro self-service: usuarios quedan pendientes de verificar su correo,
-- y su local queda pendiente de aprobacion manual del SUPER_ADMIN en backoffice.

-- DEFAULT TRUE preserva a todos los usuarios existentes y a los creados por el
-- flujo normal (UserServiceImpl.createUser, admins y backoffice/staff) como
-- verificados sin tocar ese metodo; solo el auto-registro lo pone en FALSE.
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_evt_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS IDX_evt_user ON email_verification_tokens(user_id);

ALTER TABLE stores ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(255);
ALTER TABLE stores DROP CONSTRAINT IF EXISTS CK_stores_status;
ALTER TABLE stores ADD CONSTRAINT CK_stores_status
    CHECK (status IN ('ACTIVE','INACTIVE','PENDING_VERIFICATION','SUSPENDED','REJECTED'));

CREATE INDEX IF NOT EXISTS IDX_users_email_verified ON users(email_verified);
