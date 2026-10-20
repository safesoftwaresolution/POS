package com.sciencebot.pos.settings;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Valor dentro de un tema de parámetros")
public record ParameterValueDto(
        @Schema(description = "Identificador del valor", example = "10")
        Long id,
        @Schema(description = "Código del tema al que pertenece", example = "PAYMENT_METHODS")
        String topicCode,
        @Schema(description = "Código único del valor dentro del tema", example = "NEQUI")
        String code,
        @Schema(description = "Etiqueta legible del valor", example = "Nequi / Daviplata")
        String label,
        @Schema(description = "Dato auxiliar opcional (porcentaje, costo, código DIAN…)", example = "42")
        String extraValue,
        @Schema(description = "Orden de presentación", example = "2")
        Integer sortOrder,
        @Schema(description = "Indica si el valor está activo", example = "true")
        boolean active
) {}
