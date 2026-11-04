package com.sciencebot.pos.purchases;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Ítem de una compra")
public record CreatePurchaseItemCommand(
        @Schema(description = "ID del producto comprado", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        Long productId,
        @Schema(description = "Cantidad de unidades compradas", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
        int quantity,
        @Schema(description = "Costo unitario del producto en esta compra", example = "2500.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal unitCost,
        @Schema(description = "Vencimiento de la mercancía que llega (opcional). Si se envía, la compra guarda un lote con estas unidades",
                example = "2027-01-15", nullable = true)
        LocalDate expirationDate,
        @Schema(description = "Código de lote del proveedor (opcional, solo con expirationDate)", example = "L-2210", nullable = true)
        String lotCode
) {
    public CreatePurchaseItemCommand(Long productId, int quantity, BigDecimal unitCost) {
        this(productId, quantity, unitCost, null, null);
    }
}
