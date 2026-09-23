package com.sciencebot.pos.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateNumberingRangeCurrentRequest(
        @Schema(description = "Nuevo consecutivo actual: el próximo número a usar", example = "150")
        @NotNull(message = "El consecutivo actual es obligatorio")
        @Positive(message = "El consecutivo actual debe ser mayor que cero")
        Long current
) {}
