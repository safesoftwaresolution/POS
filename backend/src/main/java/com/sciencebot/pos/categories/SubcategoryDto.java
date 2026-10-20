package com.sciencebot.pos.categories;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos completos de una subcategoría de tienda")
public record SubcategoryDto(
    @Schema(description = "Identificador único de la subcategoría", example = "1")
    Long id,
    @Schema(description = "ID de la categoría global a la que pertenece", example = "2")
    Long categoryId,
    @Schema(description = "Nombre de la categoría global a la que pertenece", example = "Bebidas")
    String categoryName,
    @Schema(description = "Nombre de la subcategoría", example = "Gaseosas")
    String name,
    @Schema(description = "Descripción de la subcategoría", example = "Bebidas carbonatadas")
    String description,
    @Schema(description = "Número de productos de esta tienda asociados a esta subcategoría", example = "8")
    int productCount
) {}
