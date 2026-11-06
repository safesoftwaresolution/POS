package com.sciencebot.pos.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Cambio de etapa del proceso de habilitación de facturación electrónica de un local")
public record TransitionBillingStatusRequest(
        @Schema(description = "Etapa destino", example = "ACTIVE")
        @NotNull(message = "El estado destino es obligatorio")
        BillingActivationStatus status,

        @Schema(description = "Motivo. Obligatorio para SUSPENDED", example = "Uso indebido de la facturación")
        String reason
) {}
