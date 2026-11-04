package com.sciencebot.pos.credit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Un movimiento de la cuenta de fiado")
public record CreditMovementDto(
        @Schema(description = "Identificador del movimiento", example = "12")
        Long id,
        @Schema(description = "Cliente dueño de la cuenta", example = "5")
        Long customerId,
        @Schema(description = "Tipo: CHARGE (venta a fiado), PAYMENT (abono) o RETURN (devolución)", example = "PAYMENT")
        CreditMovementType type,
        @Schema(description = "Monto del movimiento (siempre positivo)", example = "20000.00")
        BigDecimal amount,
        @Schema(description = "Deuda del cliente justo después del movimiento", example = "15000.00")
        BigDecimal balanceAfter,
        @Schema(description = "Venta asociada (en ventas a fiado y devoluciones)", example = "100")
        Long saleId,
        @Schema(description = "Método con que se pagó el abono (CASH, TRANSFER...)", example = "CASH")
        String paymentMethod,
        @Schema(description = "Fecha límite de pago acordada (en ventas a fiado)")
        LocalDate dueDate,
        @Schema(description = "Nota libre", example = "Pagó el viernes")
        String note,
        @Schema(description = "Usuario que registró el movimiento", example = "ana")
        String username,
        @Schema(description = "Cuándo se registró")
        LocalDateTime createdAt
) {
}
