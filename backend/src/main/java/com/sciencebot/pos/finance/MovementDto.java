package com.sciencebot.pos.finance;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Movimiento manual de dinero del local: un egreso o un ingreso")
public record MovementDto(
        Long id,
        @Schema(description = "EXPENSE (sale dinero) o INCOME (entra dinero)") MovementType type,
        @Schema(example = "Servicios") String category,
        @Schema(example = "Factura de energía de octubre") String description,
        @Schema(example = "180000.00") BigDecimal amount,
        @Schema(description = "Código del catálogo PAYMENT_METHODS", example = "CASH") String paymentMethod,
        @Schema(description = "Día real del movimiento", example = "2026-11-20") LocalDate movementDate,
        @Schema(description = "Quién lo registró") String username
) {}
