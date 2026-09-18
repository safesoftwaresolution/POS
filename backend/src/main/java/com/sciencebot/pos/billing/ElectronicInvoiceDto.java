package com.sciencebot.pos.billing;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Datos de la factura electrónica generada por Factus/DIAN")
public record ElectronicInvoiceDto(
        @Schema(description = "Identificador interno de la factura electrónica", example = "1")
        Long id,
        @Schema(description = "ID de la tienda o local", example = "1")
        Long storeId,
        @Schema(description = "ID de la venta asociada", example = "5")
        Long saleId,
        @Schema(description = "Número de factura asignado por Factus", example = "SETP990000001")
        String factusNumber,
        @Schema(description = "Código Único de Factura Electrónica (CUFE) de la DIAN", example = "fe0abc1234...")
        String cufe,
        @Schema(description = "Contenido del código QR para verificación DIAN")
        String qrCode,
        @Schema(description = "Estado de la factura: PENDING, VALIDATED, ERROR", example = "VALIDATED",
                allowableValues = {"PENDING", "VALIDATED", "ERROR"})
        String status,
        @Schema(description = "Mensaje de error si status=ERROR", example = "null — solo presente si hay error")
        String errorMessage,
        @Schema(description = "URL al PDF de la factura en el portal Factus",
                example = "https://factus.com.co/invoices/SETP990000001.pdf")
        String pdfUrl,
        @Schema(description = "Fecha y hora de validación por la DIAN", example = "2025-03-15T10:35:00")
        LocalDateTime validatedAt
) {
    public ElectronicInvoiceDto(Long id, Long saleId, String factusNumber, String cufe, String qrCode,
                                String status, String errorMessage, String pdfUrl, LocalDateTime validatedAt) {
        this(id, null, saleId, factusNumber, cufe, qrCode, status, errorMessage, pdfUrl, validatedAt);
    }
}
