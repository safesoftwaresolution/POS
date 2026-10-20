package com.sciencebot.pos.sales;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Datos requeridos para registrar una nueva venta")
public record CreateSaleCommand(
        @Schema(description = "ID del cliente (opcional — null para venta a consumidor final)", example = "3")
        Long customerId,
        @Schema(description = "Efectivo recibido del cliente", example = "50000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal cashReceived,
        @Schema(description = "Emitir automáticamente a Factus/DIAN (opcional — false para solo guardar en POS y emitir manualmente después)", example = "false")
        Boolean sendToFactus,
        @Schema(description = "Lista de productos a vender", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CreateSaleItemCommand> items,
        @Schema(description = "Código del método de pago (valor del tema PAYMENT_METHODS: CASH, NEQUI, CARD, TRANSFER...). Por defecto CASH.", example = "CASH")
        String paymentMethod
) {}
