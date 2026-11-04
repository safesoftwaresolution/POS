package com.sciencebot.pos.users;

/**
 * Publicado cuando un administrador (o el back-office) restablece la contrasena de OTRA cuenta.
 * {@code auth} lo escucha para cerrar las sesiones abiertas de esa cuenta y levantar su bloqueo por intentos.
 */
public record PasswordResetByAdminEvent(Long userId, String username) {
}
