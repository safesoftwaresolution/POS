package com.sciencebot.pos.billing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos para registrar la resolución que la DIAN entregó al local. Con Alegra el sistema lleva la numeración, así que
 * el rango completo es obligatorio: consecutivo inicial y final, vigencia y clave técnica.
 */
public record CreateNumberingRangeRequest(
        @Schema(description = "Código del tipo de documento, según la tabla de referencia del proveedor")
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
        Long current,
        @Schema(description = "Consecutivo inicial de la resolución. Solo lo usa Alegra (obligatorio allí); el proveedor lo ignora", example = "990000000")
        Long from,
        @Schema(description = "Consecutivo final de la resolución. Solo lo usa Alegra (obligatorio allí); el proveedor lo ignora", example = "995000000")
        Long to,
        @Schema(description = "Inicio de vigencia (AAAA-MM-DD). Opcional; el proveedor lo ignora", example = "2026-01-01")
        String startDate,
        @Schema(description = "Fin de vigencia (AAAA-MM-DD). Opcional; el proveedor lo ignora", example = "2027-01-01")
        String endDate,
        @Schema(description = "Clave técnica de la resolución. Opcional")
        String technicalKey
) {
    /** Forma original (la del proveedor): sin los datos de rango que solo Alegra necesita. */
    public CreateNumberingRangeRequest(String document, String prefix, String resolutionNumber, Long current) {
        this(document, prefix, resolutionNumber, current, null, null, null, null, null);
    }
}
