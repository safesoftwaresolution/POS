package com.sciencebot.pos.settings;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos para actualizar un valor de un tema de parámetros. Los campos nulos no se modifican.")
public record UpdateParameterValueCommand(
        @Schema(description = "Nueva etiqueta legible del valor", example = "Nequi")
        String label,
        @Schema(description = "Nuevo dato auxiliar (porcentaje, costo, código DIAN…)", example = "42")
        String extraValue,
        @Schema(description = "Nuevo orden de presentación", example = "3")
        Integer sortOrder,
        @Schema(description = "Nuevo estado activo/inactivo", example = "true")
        Boolean active
) {}
