package com.sciencebot.pos.credit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Abono a la deuda de un cliente")
public record RegisterPaymentCommand(
        @Schema(description = "Monto del abono (mayor a cero y sin superar la deuda)", example = "20000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount,
        @Schema(description = "Cómo pagó el cliente: CASH, TRANSFER, NEQUI, CARD... (no puede ser CREDIT)", example = "CASH")
        String paymentMethod,
        @Schema(description = "Nota opcional", example = "Pagó el viernes")
        String note
) {
}
