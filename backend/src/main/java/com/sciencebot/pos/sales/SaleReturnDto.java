package com.sciencebot.pos.sales;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Devolución de uno o varios ítems de una venta ya registrada")
public record SaleReturnDto(
        @Schema(description = "Identificador de la devolución", example = "1")
        Long id,
        @Schema(description = "ID de la venta original", example = "5")
        Long saleId,
        @Schema(description = "Número de factura de la venta original", example = "FACT-000005")
        String saleInvoiceNumber,
        @Schema(description = "Motivo de la devolución", example = "Producto defectuoso")
        String reason,
        @Schema(description = "Monto total a reembolsar", example = "9600.00")
        BigDecimal totalRefund,
        @Schema(description = "Username de quien procesó la devolución", example = "cajero01")
        String processedByUsername,
        @Schema(description = "Fecha y hora de la devolución", example = "2025-03-16T09:12:00")
        LocalDateTime createdAt,
        @Schema(description = "Ítems devueltos")
        List<SaleReturnItemDto> items
) {}
