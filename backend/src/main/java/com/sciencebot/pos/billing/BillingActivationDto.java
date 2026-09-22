package com.sciencebot.pos.billing;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Estado de la facturación electrónica de un local. Nunca incluye secretos: solo indica
 * si cada entorno tiene las credenciales completas.
 */
@Schema(description = "Estado de la facturación electrónica de un local (sin credenciales)")
public record BillingActivationDto(
        Long storeId,
        BillingActivationStatus status,
        String statusReason,
        BillingEnvironment environment,
        boolean sandboxConfigured,
        boolean productionConfigured,
        Integer sandboxNumberingRangeId,
        Integer productionNumberingRangeId,
        LocalDateTime updatedAt
) {}
