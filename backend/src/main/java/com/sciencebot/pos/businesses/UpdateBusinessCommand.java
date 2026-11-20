package com.sciencebot.pos.businesses;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Only the non-null fields change. */
public record UpdateBusinessCommand(
        @Size(min = 1, max = 100, message = "El nombre del negocio debe tener entre 1 y 100 caracteres.")
        String name,
        @Min(value = 1, message = "El máximo de sucursales debe ser al menos 1.")
        Integer maxBranches
) {}
