package com.sciencebot.pos.backoffice;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos requeridos para crear un nuevo operador SUPER_ADMIN del backoffice")
public record CreateBackofficeStaffCommand(
    @Schema(description = "Nombre completo", example = "Ana Torres", requiredMode = Schema.RequiredMode.REQUIRED)
    String fullName,
    @Schema(description = "Nombre de usuario unico para login", example = "ana.torres", requiredMode = Schema.RequiredMode.REQUIRED)
    String username,
    @Schema(description = "Correo electronico", example = "ana.torres@platform.internal", requiredMode = Schema.RequiredMode.REQUIRED)
    String email,
    @Schema(description = "Contrasena (minimo 8 caracteres, letras y numeros)", example = "ClaveSegura123", requiredMode = Schema.RequiredMode.REQUIRED)
    String password
) {}
