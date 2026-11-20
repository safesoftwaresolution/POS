package com.sciencebot.pos.businesses;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBusinessCommand(
        @NotBlank(message = "El nombre del negocio es obligatorio.")
        @Size(max = 100, message = "El nombre del negocio no puede superar 100 caracteres.")
        String name,
        @NotNull(message = "El dueño es obligatorio.")
        Long ownerUserId,
        @NotNull(message = "La sucursal principal es obligatoria.")
        Long mainStoreId,
        @NotNull(message = "El modo de facturación es obligatorio.")
        BillingMode billingMode,
        /** Optional: defaults to {@code INDEPENDENT}; {@code SHARED} is rejected until phase 2. */
        CatalogMode catalogMode,
        @NotNull(message = "El máximo de sucursales es obligatorio.")
        @Min(value = 1, message = "El máximo de sucursales debe ser al menos 1.")
        Integer maxBranches
) {}
