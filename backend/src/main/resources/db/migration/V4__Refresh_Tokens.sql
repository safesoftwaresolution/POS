-- V4__Refresh_Tokens.sql
-- Soporte de refresh tokens: tokens opacos, revocables y de larga duracion
-- almacenados como hash SHA-256 para permitir renovar el access token JWT.
-- La rotacion agrupa los tokens de una misma sesion en una "familia" (family_id)
-- para poder revocarla completa ante deteccion de reuso (token robado).

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT FK_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- token_hash ya tiene indice unico implicito; no se duplica.
CREATE INDEX IF NOT EXISTS IDX_refresh_tokens_user    ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS IDX_refresh_tokens_family  ON refresh_tokens(family_id);
CREATE INDEX IF NOT EXISTS IDX_refresh_tokens_expires ON refresh_tokens(expires_at);
