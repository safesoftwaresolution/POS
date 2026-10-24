package com.sciencebot.pos.sales;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ítem de una venta (producto y cantidad)")
public record CreateSaleItemCommand(
        @jakarta.validation.constraints.NotNull
        @Schema(description = "ID del producto a vender", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        Long productId,
        @jakarta.validation.constraints.Positive
        @Schema(description = "Cantidad a vender", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        int quantity
) {}
