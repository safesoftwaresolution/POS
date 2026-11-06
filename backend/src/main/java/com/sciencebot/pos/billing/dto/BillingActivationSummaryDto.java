package com.sciencebot.pos.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lo mínimo que el POS necesita para ofrecer la factura electrónica al cobrar: si el local puede facturar ahora y en
 * qué entorno (en SANDBOX las facturas son de prueba y no tienen validez legal).
 */
@Schema(description = "Si el local puede emitir facturas electrónicas ahora y en qué entorno")
public record BillingActivationSummaryDto(
        @Schema(description = "true solo si la facturación electrónica del local está ACTIVE")
        boolean active,
        @Schema(description = "SANDBOX = facturas de prueba sin validez legal; PRODUCTION = facturas reales ante la DIAN")
        BillingEnvironment environment
) {

    public static BillingActivationSummaryDto of(BillingActivationDto activation) {
        return new BillingActivationSummaryDto(activation.status() == BillingActivationStatus.ACTIVE,
                activation.environment());
    }
}
