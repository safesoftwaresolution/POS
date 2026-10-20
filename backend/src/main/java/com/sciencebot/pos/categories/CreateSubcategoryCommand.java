package com.sciencebot.pos.categories;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos requeridos para crear una subcategoría de tienda dentro de una categoría global existente")
public record CreateSubcategoryCommand(
    @Schema(description = "ID de la categoría global a la que pertenece", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    Long categoryId,
    @Schema(description = "Nombre de la subcategoría (único dentro de la categoría, para esta tienda)", example = "Gaseosas", requiredMode = Schema.RequiredMode.REQUIRED)
    String name,
    @Schema(description = "Descripción opcional de la subcategoría", example = "Bebidas carbonatadas")
    String description
) {}
