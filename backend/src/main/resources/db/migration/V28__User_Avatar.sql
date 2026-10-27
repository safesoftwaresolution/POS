-- Foto de perfil del usuario: URL publica en R2 (imagenes-avatares/...). Nula = se muestran las iniciales.
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
