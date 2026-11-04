package com.sciencebot.pos.sales;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ítem de una venta: un producto o un servicio (exactamente uno de los dos) y su cantidad")
public record CreateSaleItemCommand(
        @Schema(description = "ID del producto a vender (omitir si es un servicio)", example = "5", nullable = true)
        Long productId,
        @Schema(description = "ID del servicio a vender (omitir si es un producto)", example = "2", nullable = true)
        Long serviceId,
        @jakarta.validation.constraints.Positive
        @Schema(description = "Cantidad a vender", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        int quantity
) {
    /** Línea de producto (forma original del comando). */
    public CreateSaleItemCommand(Long productId, int quantity) {
        this(productId, null, quantity);
    }
}
