package com.sciencebot.pos.settings;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos para crear un nuevo tema de parámetros")
public record CreateParameterTopicCommand(
        @Schema(description = "Código único del tema (se normaliza a mayúsculas)", example = "DELIVERY_ZONES", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Nombre legible del tema", example = "Zonas de Entrega", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Descripción opcional del tema", example = "Zonas de despacho y su costo asociado")
        String description
) {}
