package com.sciencebot.pos.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credenciales para iniciar sesión")
public record LoginRequest(
    @Schema(description = "Nombre de usuario o correo", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    String username,

    @Schema(description = "Contraseña", example = "admin123", requiredMode = Schema.RequiredMode.REQUIRED)
    String password
) {
    public LoginRequest {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Los campos username y password son requeridos");
        }
    }
}
