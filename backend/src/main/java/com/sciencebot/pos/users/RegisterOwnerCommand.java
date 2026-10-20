package com.sciencebot.pos.users;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos para el auto-registro publico del futuro dueno de un local")
public record RegisterOwnerCommand(
    @Schema(description = "Nombre completo", example = "Carlos Martinez", requiredMode = Schema.RequiredMode.REQUIRED)
    String fullName,
    @Schema(description = "Nombre de usuario unico para login", example = "carlos.m", requiredMode = Schema.RequiredMode.REQUIRED)
    String username,
    @Schema(description = "Correo electronico, se le enviara un enlace de verificacion", example = "carlos@empresa.com", requiredMode = Schema.RequiredMode.REQUIRED)
    String email,
    @Schema(description = "Contrasena (minimo 8 caracteres, letra y numero)", example = "MiClave123!", requiredMode = Schema.RequiredMode.REQUIRED)
    String password
) {}
