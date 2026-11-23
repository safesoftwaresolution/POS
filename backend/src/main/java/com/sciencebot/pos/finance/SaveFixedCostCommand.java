package com.sciencebot.pos.finance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Datos para crear o editar un costo fijo")
public record SaveFixedCostCommand(
        @NotBlank @Size(max = 100) @Schema(example = "Arriendo", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @NotNull @Schema(example = "1500000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal monthlyAmount,
        @Min(1) @Max(31) @Schema(description = "Día del mes en que se paga (1 a 31); opcional. En meses más cortos vale el último día", example = "5") Integer dueDay,
        @Schema(description = "Por defecto true") Boolean active
) {}
