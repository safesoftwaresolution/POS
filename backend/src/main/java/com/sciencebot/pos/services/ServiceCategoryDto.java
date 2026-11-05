package com.sciencebot.pos.services;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Categoría de servicios (catálogo global, distinto de las categorías de productos)")
public record ServiceCategoryDto(
    @Schema(description = "Identificador único", example = "4")
    Long id,
    @Schema(description = "Nombre", example = "Corte")
    String name,
    @Schema(description = "Descripción", example = "Cortes de cabello")
    String description,
    @Schema(description = "Si está disponible para asignar a servicios", example = "true")
    boolean active,
    @Schema(description = "Categorías de local donde se ofrece; vacío = disponible para todos los locales")
    List<Long> storeCategoryIds
) {}
