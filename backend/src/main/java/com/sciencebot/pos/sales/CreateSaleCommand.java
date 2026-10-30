package com.sciencebot.pos.sales;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Datos requeridos para registrar una nueva venta")
public record CreateSaleCommand(
        @Schema(description = "ID del cliente (opcional — null para venta a consumidor final; obligatorio si el pago es CREDIT)", example = "3")
        Long customerId,
        @Schema(description = "Efectivo recibido del cliente (se ignora si el pago es CREDIT)", example = "50000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal cashReceived,
        @Schema(description = "Emitir automáticamente a Factus/DIAN (opcional — false para solo guardar en POS y emitir manualmente después)", example = "false")
        Boolean sendToFactus,
        @jakarta.validation.constraints.NotEmpty @jakarta.validation.Valid
        @Schema(description = "Lista de productos a vender", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CreateSaleItemCommand> items,
        @Schema(description = "Código del método de pago (valor del tema PAYMENT_METHODS: CASH, NEQUI, CARD, TRANSFER, CREDIT...). Por defecto CASH. "
                + "CREDIT = fiado: el total se suma a la deuda del cliente en vez de cobrarse.", example = "CASH")
        String paymentMethod,
        @Schema(description = "Solo para CREDIT: fecha límite de pago acordada con el cliente (por defecto, 30 días desde hoy)", example = "2026-12-01")
        LocalDate creditDueDate
) {
    /** Venta sin fecha de pago acordada (la mayoría de las ventas: contado). */
    public CreateSaleCommand(Long customerId, BigDecimal cashReceived, Boolean sendToFactus,
                             List<CreateSaleItemCommand> items, String paymentMethod) {
        this(customerId, cashReceived, sendToFactus, items, paymentMethod, null);
    }
}
