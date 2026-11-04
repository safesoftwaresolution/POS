package com.sciencebot.pos.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta de autenticación con tokens y datos de sesión")
public record LoginResponse(
        @Schema(description = "Token de acceso JWT", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String token,

        @Schema(description = "Token de refresco", example = "dGhpcy1pcy1hLXJlZnJlc2gtdG9rZW4...")
        String refreshToken,

        @Schema(description = "Nombre de usuario autenticado", example = "admin")
        String username,

        @Schema(description = "Rol del usuario", example = "ADMINISTRATOR")
        String role,

        @Schema(description = "ID de la tienda asociada (si aplica)", example = "1")
        Long storeId,

        @Schema(description = "Tiempo de expiración en segundos del access token", example = "900")
        long expiresIn,

        @Schema(description = "Nombre completo del usuario", example = "Carlos Martinez")
        String fullName,

        @Schema(description = "URL de la foto de perfil (nula si no tiene)")
        String avatarUrl
) {
    public LoginResponse(String token, String refreshToken, String username, String role, Long storeId,
                         long expiresIn) {
        this(token, refreshToken, username, role, storeId, expiresIn, null, null);
    }
}
