package com.sciencebot.pos.services;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Datos para crear o actualizar una categoría de servicios")
public record CreateServiceCategoryCommand(
    @Schema(description = "Nombre (único)", example = "Maquillaje", requiredMode = Schema.RequiredMode.REQUIRED)
    String name,
    @Schema(description = "Descripción", example = "Maquillaje social y de novia")
    String description,
    @Schema(description = "Categorías de local donde se ofrece; vacío = todos los locales")
    List<Long> storeCategoryIds
) {}
