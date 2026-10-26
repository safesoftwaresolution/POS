package com.sciencebot.pos.inventory;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Datos requeridos para registrar un movimiento de inventario")
public record CreateMovementCommand(
        @Schema(description = "ID del producto a mover", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        Long productId,
        @Schema(description = "Tipo de movimiento: ENTRY (entrada), EXIT (salida), ADJUSTMENT (ajuste), RETURN (devolución)",
                example = "ENTRY", allowableValues = {"ENTRY", "EXIT", "ADJUSTMENT", "RETURN"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String movementType,
        @Schema(description = "Cantidad de unidades a mover", example = "50", requiredMode = Schema.RequiredMode.REQUIRED)
        int quantity,
        @Schema(description = "Motivo del movimiento", example = "Recepción de mercancía proveedor Central")
        String reason,
        @Schema(description = "Vencimiento de la mercancía que entra (opcional, solo en entradas). Si se envía, la entrada crea un lote con estas unidades",
                example = "2027-01-15", nullable = true)
        LocalDate expirationDate,
        @Schema(description = "Código de lote del proveedor (opcional, solo con expirationDate)", example = "L-2210", nullable = true)
        String lotCode
) {
    public CreateMovementCommand(Long productId, String movementType, int quantity, String reason) {
        this(productId, movementType, quantity, reason, null, null);
    }
}
