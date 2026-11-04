package com.sciencebot.pos.credit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Cupo máximo de deuda de un cliente")
public record SetCreditLimitCommand(
        @Schema(description = "Cupo máximo; null o ausente quita el límite", example = "200000.00")
        BigDecimal creditLimit
) {
}
