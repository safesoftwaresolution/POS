package com.sciencebot.pos.credit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Cuenta de fiado de un cliente: cuánto debe y cuánto se le puede fiar")
public record CreditAccountDto(
        @Schema(description = "Identificador del cliente", example = "5")
        Long customerId,
        @Schema(description = "Nombre del cliente", example = "Juan García")
        String customerName,
        @Schema(description = "Identificación del cliente", example = "1234567890")
        String identification,
        @Schema(description = "Lo que el cliente debe hoy", example = "35000.00")
        BigDecimal balance,
        @Schema(description = "Cupo máximo de deuda (null = sin límite)", example = "200000.00")
        BigDecimal creditLimit,
        @Schema(description = "Cupo disponible: cupo menos deuda (null si no hay límite)", example = "165000.00")
        BigDecimal availableCredit,
        @Schema(description = "Fecha del último movimiento de la cuenta (null si no tiene)")
        LocalDateTime lastMovementAt
) {
}
