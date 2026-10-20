package com.sciencebot.pos.sales;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ítem a devolver, referenciando la línea exacta de la venta original")
public record CreateSaleReturnItemCommand(
        @Schema(description = "ID de la línea (SaleItemDto.id) de la venta original a devolver", example = "12")
        Long saleItemId,
        @Schema(description = "Cantidad a devolver (no puede superar lo vendido menos lo ya devuelto en esa línea)", example = "1")
        int quantity
) {}
