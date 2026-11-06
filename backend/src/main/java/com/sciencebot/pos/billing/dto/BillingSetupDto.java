package com.sciencebot.pos.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Avance de la habilitación de la facturación electrónica del local, paso a paso. */
@Schema(description = "Asistente de habilitación de la facturación electrónica")
public record BillingSetupDto(
        @Schema(description = "Etapa de la habilitación", example = "IN_SETUP")
        BillingActivationStatus status,
        @Schema(description = "Entorno con el que factura el local", example = "PRODUCTION")
        BillingEnvironment environment,
        @Schema(description = "La plataforma ya puede dar de alta empresas en producción")
        boolean platformReady,
        @Schema(description = "Pasos en orden: FISCAL_DATA, DIAN_ENABLEMENT, NUMBERING, READY")
        List<BillingSetupStepDto> steps,
        @Schema(description = "Datos fiscales guardados, o una sugerencia con los datos del local si aún no hay")
        FiscalProfileDto profile,
        @Schema(description = "Rangos de facturas registrados en producción", example = "1")
        int invoiceRangesCount
) {}
