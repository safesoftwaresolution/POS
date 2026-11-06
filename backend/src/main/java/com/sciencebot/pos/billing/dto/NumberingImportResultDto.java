package com.sciencebot.pos.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Resultado de importar las resoluciones de la DIAN como rangos de numeración del local. */
@Schema(description = "Resultado de la importación de la numeración")
public record NumberingImportResultDto(
        @Schema(description = "Resoluciones de facturas importadas", example = "1")
        int importedCount,
        @Schema(description = "Rangos de notas crédito y débito creados (no llevan resolución)", example = "2")
        int noteRangesCreated,
        @Schema(description = "Resoluciones que no se importaron y por qué")
        List<String> skipped,
        @Schema(description = "Cómo queda el asistente")
        BillingSetupDto setup
) {}
