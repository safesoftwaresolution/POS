package com.sciencebot.pos.finance;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Costo fijo mensual recurrente (arriendo, nómina, servicios…)")
public record FixedCostDto(
        Long id,
        @Schema(example = "Arriendo") String name,
        @Schema(example = "1500000.00") BigDecimal monthlyAmount,
        @Schema(description = "Día del mes en que se paga (1 a 31); null si no tiene día fijo", example = "5") Integer dueDay,
        boolean active
) {}
