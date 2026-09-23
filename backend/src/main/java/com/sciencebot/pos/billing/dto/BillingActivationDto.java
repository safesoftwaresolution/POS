package com.sciencebot.pos.billing.dto;

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
        @Schema(description = "Rango de numeración que el local eligió para sandbox (solo lectura para el back office)")
        Integer sandboxNumberingRangeId,
        @Schema(description = "Rango de numeración que el local eligió para producción (solo lectura para el back office)")
        Integer productionNumberingRangeId,
        LocalDateTime updatedAt
) {}
