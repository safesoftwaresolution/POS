package com.sciencebot.pos.stores;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Alta de un local (self-service o backoffice). Los topes coinciden con las columnas de {@code stores}. */
public record CreateStoreCommand(
    @NotBlank(message = "El nombre del local es obligatorio")
    @Size(max = 100, message = "El nombre del local admite máximo 100 caracteres")
    String name,
    Long storeCategoryId,
    @Size(max = 20, message = "El teléfono admite máximo 20 caracteres")
    String phone,
    @NotBlank(message = "El correo del local es obligatorio")
    @Email(message = "El correo del local no es válido")
    @Size(max = 100, message = "El correo del local admite máximo 100 caracteres")
    String email,
    @Size(max = 255, message = "El sitio web admite máximo 255 caracteres")
    String website,
    @Size(max = 255, message = "La dirección admite máximo 255 caracteres")
    String address,
    @Size(max = 50, message = "El NIT admite máximo 50 caracteres")
    String taxId
) {}
