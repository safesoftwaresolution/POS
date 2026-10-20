package com.sciencebot.pos.backoffice;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos actualizables de un operador SUPER_ADMIN existente")
public record UpdateBackofficeStaffCommand(
    @Schema(description = "Nuevo nombre completo", example = "Ana Torres Gomez")
    String fullName,
    @Schema(description = "Nuevo correo electronico", example = "ana.torres.gomez@platform.internal")
    String email
) {}
