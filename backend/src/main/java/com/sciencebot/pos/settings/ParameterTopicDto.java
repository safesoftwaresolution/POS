package com.sciencebot.pos.settings;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tema (encabezado) de un catálogo de parámetros")
public record ParameterTopicDto(
        @Schema(description = "Identificador del tema", example = "1")
        Long id,
        @Schema(description = "Código único del tema", example = "PAYMENT_METHODS")
        String code,
        @Schema(description = "Nombre legible del tema", example = "Métodos de Pago")
        String name,
        @Schema(description = "Descripción del tema", example = "Formas de pago aceptadas en el punto de venta")
        String description,
        @Schema(description = "Indica si es un tema base del sistema (protegido)", example = "true")
        boolean isSystem
) {}
