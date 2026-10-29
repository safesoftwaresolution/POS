package com.sciencebot.pos.sales;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Ítem devuelto dentro de una devolución de venta")
public record SaleReturnItemDto(
        @Schema(description = "ID de la línea de la venta original que se devolvió", example = "12")
        Long saleItemId,
        @Schema(description = "ID del producto devuelto (null si la línea era un servicio)", example = "3", nullable = true)
        Long productId,
        @Schema(description = "Nombre del producto o servicio", example = "Arroz Diana 500g")
        String productName,
        @Schema(description = "Cantidad devuelta", example = "1")
        int quantity,
        @Schema(description = "Precio unitario al momento de la venta original", example = "3200.00")
        BigDecimal unitPrice,
        @Schema(description = "Subtotal reembolsado de este ítem", example = "3200.00")
        BigDecimal subtotal,
        @Schema(description = "ID del servicio devuelto (null si la línea era un producto)", example = "2", nullable = true)
        Long serviceId
) {
    public SaleReturnItemDto(Long saleItemId, Long productId, String productName, int quantity,
                             BigDecimal unitPrice, BigDecimal subtotal) {
        this(saleItemId, productId, productName, quantity, unitPrice, subtotal, null);
    }
}
