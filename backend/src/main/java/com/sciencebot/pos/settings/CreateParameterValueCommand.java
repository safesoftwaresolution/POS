package com.sciencebot.pos.settings;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos para agregar un valor a un tema de parámetros")
public record CreateParameterValueCommand(
        @Schema(description = "Código único del valor dentro del tema (se normaliza a mayúsculas)", example = "NEQUI", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Etiqueta legible del valor", example = "Nequi / Daviplata", requiredMode = Schema.RequiredMode.REQUIRED)
        String label,
        @Schema(description = "Dato auxiliar opcional (porcentaje, costo, código DIAN…)", example = "42")
        String extraValue,
        @Schema(description = "Orden de presentación (por defecto 0)", example = "2")
        Integer sortOrder
) {}
