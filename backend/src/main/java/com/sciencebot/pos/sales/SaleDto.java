package com.sciencebot.pos.sales;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Datos completos de una venta")
public record SaleDto(
        @Schema(description = "Identificador único de la venta", example = "1")
        Long id,
        @Schema(description = "Identificador del local (tienda) donde se realizó la venta", example = "1")
        Long storeId,
        @Schema(description = "Identificador único del cliente (null si es consumidor final)", example = "123")
        Long customerId,
        @Schema(description = "Número de factura interno del POS", example = "FAC-2025-0001")
        String invoiceNumber,
        @Schema(description = "Fecha y hora de la venta", example = "2025-03-15T10:30:00")
        LocalDateTime createdAt,
        @Schema(description = "Nombre del cliente (null si es consumidor final)", example = "Juan García")
        String customerName,
        @Schema(description = "Monto total de la venta", example = "45000.00")
        BigDecimal totalAmount,
        @Schema(description = "Efectivo recibido del cliente", example = "50000.00")
        BigDecimal cashReceived,
        @Schema(description = "Cambio a devolver al cliente", example = "5000.00")
        BigDecimal cashChange,
        @Schema(description = "Username del vendedor que registró la venta", example = "seller01")
        String sellerUsername,
        @Schema(description = "Lista de productos vendidos")
        List<SaleItemDto> items,
        @Schema(description = "Código del método de pago usado (CASH, NEQUI, CARD, TRANSFER...)", example = "CASH")
        String paymentMethod
) {
    public SaleDto(Long id, String invoiceNumber, LocalDateTime createdAt, String customerName,
                   BigDecimal totalAmount, BigDecimal cashReceived, BigDecimal cashChange,
                   String sellerUsername, List<SaleItemDto> items, String paymentMethod) {
        this(id, null, null, invoiceNumber, createdAt, customerName, totalAmount, cashReceived, cashChange, sellerUsername, items, paymentMethod);
    }
}
