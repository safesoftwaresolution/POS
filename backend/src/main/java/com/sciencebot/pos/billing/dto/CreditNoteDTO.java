package com.sciencebot.pos.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Datos de la Nota Crédito electrónica generada y validada por Factus y la DIAN")
public record CreditNoteDTO(
        @Schema(description = "Identificador interno de la nota crédito", example = "1")
        Long id,

        @Schema(description = "ID del local o tienda", example = "1")
        Long storeId,

        @Schema(description = "ID de la venta asociada (si aplica)", example = "10")
        Long saleId,

        @Schema(description = "Código único de referencia local de la nota crédito", example = "NOTA-2026-0015")
        String referenceCode,

        @Schema(description = "Número legal de la factura electrónica asociada", example = "SETP990002519")
        String billNumber,

        @Schema(description = "Número legal oficial asignado por Factus/DIAN a la nota crédito", example = "NC990000001")
        String creditNoteNumber,

        @Schema(description = "Código de concepto de corrección", example = "2")
        String correctionConceptCode,

        @Schema(description = "Código de tipo de operación (20 o 22)", example = "20")
        String customizationId,

        @Schema(description = "Código Único de Documento Electrónico (CUDE) asignado por la DIAN", example = "cude-fe0abc1234...")
        String cude,

        @Schema(description = "Contenido o enlace del código QR de validación fiscal")
        String qrCode,

        @Schema(description = "Estado fiscal: PENDING, VALIDATED, REJECTED, ERROR", example = "VALIDATED",
                allowableValues = {"PENDING", "VALIDATED", "REJECTED", "ERROR"})
        String status,

        @Schema(description = "Detalle del error técnico o motivo de rechazo DIAN", example = "null")
        String errorMessage,

        @Schema(description = "Enlace público al PDF oficial de la nota crédito en Factus",
                example = "https://api.factus.com.co/v1/bills/download-pdf/NC990000001")
        String pdfUrl,

        @Schema(description = "Fecha y hora de certificación y validación por la DIAN", example = "2026-09-24T16:00:00")
        LocalDateTime validatedAt,

        @Schema(description = "Fecha y hora de creación del registro local", example = "2026-09-24T15:58:00")
        LocalDateTime createdAt
) {}
