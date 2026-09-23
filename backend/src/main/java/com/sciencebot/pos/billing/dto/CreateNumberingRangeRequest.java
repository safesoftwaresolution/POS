package com.sciencebot.pos.billing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos para registrar en Factus la resolución que la DIAN entregó al local. Factus completa
 * el rango (desde, hasta, fechas y clave técnica) a partir de la resolución, por eso no se piden aquí.
 */
public record CreateNumberingRangeRequest(
        @Schema(description = "Código del tipo de documento, según la tabla de referencia de Factus")
        @NotBlank(message = "El código de documento es obligatorio")
        String document,

        @Schema(description = "Prefijo alfanumérico del rango de numeración", example = "SETP")
        @NotBlank(message = "El prefijo es obligatorio")
        String prefix,

        @Schema(description = "Número de la resolución de facturación entregada por la DIAN", example = "18764000001234")
        @NotBlank(message = "El número de resolución es obligatorio")
        @JsonProperty("resolution_number")
        String resolutionNumber,

        @Schema(description = "Consecutivo actual: el próximo número a usar. Si ya se usaron números, el último usado más uno", example = "1")
        @NotNull(message = "El consecutivo actual es obligatorio")
        @Positive(message = "El consecutivo actual debe ser mayor que cero")
        Long current
) {}
