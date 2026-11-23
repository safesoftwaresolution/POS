package com.sciencebot.pos.finance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Datos para registrar o editar un movimiento manual")
public record SaveMovementCommand(
        @NotNull @Schema(description = "EXPENSE (sale dinero) o INCOME (entra dinero)", requiredMode = Schema.RequiredMode.REQUIRED) MovementType type,
        @NotBlank @Size(max = 60) @Schema(example = "Servicios", requiredMode = Schema.RequiredMode.REQUIRED) String category,
        @Size(max = 255) String description,
        @NotNull @Schema(example = "180000", requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal amount,
        @Size(max = 30) @Schema(description = "Por defecto CASH", example = "CASH") String paymentMethod,
        @Schema(description = "Día real del movimiento; por defecto hoy; no puede ser futuro") LocalDate movementDate
) {}
