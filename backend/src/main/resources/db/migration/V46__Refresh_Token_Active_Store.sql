-- Sucursal activa de cada sesion (businesses, U2). Un dueño con varias sucursales puede cambiar de local sin
-- cerrar sesion: el refresh token recuerda a cual, y la rotacion la conserva. NULL = el local del usuario
-- (users.store_id), que es lo que usan todas las sesiones existentes.
-- Sin FK a stores a proposito: borrar un local no debe bloquearse por sesiones; al renovar se valida que el
-- usuario siga teniendo acceso a ese local y, si no, se vuelve a su local.
ALTER TABLE refresh_tokens ADD COLUMN store_id BIGINT;
