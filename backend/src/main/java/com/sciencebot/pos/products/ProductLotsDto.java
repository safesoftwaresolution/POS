package com.sciencebot.pos.products;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Lotes con unidades de un producto y cuántas unidades del stock no tienen fecha")
public record ProductLotsDto(
        @Schema(description = "Lotes que todavía tienen unidades, del que vence primero al último")
        List<ProductLotDto> lots,
        @Schema(description = "Unidades del stock que no pertenecen a ningún lote (entraron sin fecha)", example = "0")
        int undatedQuantity
) {}
