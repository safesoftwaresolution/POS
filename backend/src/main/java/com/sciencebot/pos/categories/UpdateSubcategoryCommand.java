package com.sciencebot.pos.categories;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos para actualizar una subcategoría existente")
public record UpdateSubcategoryCommand(
    @Schema(description = "Nuevo nombre de la subcategoría", example = "Gaseosas y Maltas", requiredMode = Schema.RequiredMode.REQUIRED)
    String name,
    @Schema(description = "Nueva descripción")
    String description
) {}
