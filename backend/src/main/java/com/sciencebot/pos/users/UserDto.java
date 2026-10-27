package com.sciencebot.pos.users;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos completos de un usuario del sistema")
public record UserDto(
    @Schema(description = "Identificador unico del usuario", example = "1")
    Long id,
    @Schema(description = "Nombre completo del usuario", example = "Carlos Martinez")
    String fullName,
    @Schema(description = "Nombre de usuario para login", example = "carlos.m")
    String username,
    @Schema(description = "Correo electronico", example = "carlos@empresa.com")
    String email,
    @Schema(description = "Rol del usuario: SUPER_ADMIN, ADMINISTRATOR, SUPERVISOR, SELLER",
            example = "SUPERVISOR")
    String role,
    @Schema(description = "Si el usuario puede acceder al sistema", example = "true")
    boolean active,
    @Schema(description = "ID del local al que pertenece. Nulo para SUPER_ADMIN o para un ADMINISTRATOR que aun no ha creado su local.", example = "1")
    Long storeId,
    @Schema(description = "Si el usuario ya verifico su correo electronico", example = "true")
    boolean emailVerified,
    @Schema(description = "URL publica de la foto de perfil. Nula si el usuario no ha subido una.",
            example = "https://cdn.posbento.com/imagenes-avatares/tienda-1/3f2a.jpg")
    String avatarUrl
) {
    /** Usuario sin foto de perfil. */
    public UserDto(Long id, String fullName, String username, String email, String role,
                   boolean active, Long storeId, boolean emailVerified) {
        this(id, fullName, username, email, role, active, storeId, emailVerified, null);
    }
}