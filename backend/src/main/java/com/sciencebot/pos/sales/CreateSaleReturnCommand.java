package com.sciencebot.pos.sales;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Datos para registrar una devolución sobre una venta existente")
public record CreateSaleReturnCommand(
        @Schema(description = "Motivo de la devolución", example = "Producto defectuoso")
        String reason,
        @Schema(description = "Ítems a devolver (al menos uno)")
        List<CreateSaleReturnItemCommand> items
) {}
