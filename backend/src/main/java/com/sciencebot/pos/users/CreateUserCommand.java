package com.sciencebot.pos.users;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos requeridos para crear un nuevo usuario")
public record CreateUserCommand(
    @NotBlank @Size(max = 150)
    @Schema(description = "Nombre completo del usuario", example = "Carlos Martinez", requiredMode = Schema.RequiredMode.REQUIRED)
    String fullName,
    @NotBlank @Size(max = 50)
    @Schema(description = "Nombre de usuario unico para login", example = "carlos.m", requiredMode = Schema.RequiredMode.REQUIRED)
    String username,
    @Email @Size(max = 150)
    @Schema(description = "Correo electronico", example = "carlos@empresa.com")
    String email,
    @NotBlank
    @Schema(description = "Contrasena (minimo 8 caracteres)", example = "MiClave123!", requiredMode = Schema.RequiredMode.REQUIRED)
    String password,
    @NotBlank
    @Schema(description = "Rol del usuario: SUPER_ADMIN, ADMINISTRATOR, SUPERVISOR o SELLER",
            example = "SUPERVISOR", allowableValues = {"SUPER_ADMIN", "ADMINISTRATOR", "SUPERVISOR", "SELLER"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    String role,
    @Schema(description = "ID del local al que pertenece. Nulo para SUPER_ADMIN.", example = "1")
    Long storeId
) {}