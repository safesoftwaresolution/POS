package com.sciencebot.pos.users;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos actualizables de un usuario existente")
public record UpdateUserCommand(
    @NotBlank @Size(max = 150)
    @Schema(description = "Nuevo nombre completo", example = "Carlos Martínez Pérez")
    String fullName,
    @NotBlank @Email @Size(max = 150)
    @Schema(description = "Nuevo correo electrónico", example = "carlos.nuevo@empresa.com")
    String email,
    @NotBlank
    @Schema(description = "Nuevo rol: ADMINISTRATOR, SUPERVISOR o SELLER",
            example = "SUPERVISOR", allowableValues = {"ADMINISTRATOR", "SUPERVISOR", "SELLER"})
    String role
) {}
