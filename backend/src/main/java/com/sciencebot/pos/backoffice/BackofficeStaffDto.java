package com.sciencebot.pos.backoffice;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cuenta de operador de plataforma (SUPER_ADMIN)")
public record BackofficeStaffDto(
    @Schema(description = "Identificador unico", example = "1")
    Long id,
    @Schema(description = "Nombre completo", example = "Ana Torres")
    String fullName,
    @Schema(description = "Nombre de usuario para login", example = "ana.torres")
    String username,
    @Schema(description = "Correo electronico", example = "ana.torres@platform.internal")
    String email,
    @Schema(description = "Si la cuenta puede iniciar sesion", example = "true")
    boolean active
) {}
