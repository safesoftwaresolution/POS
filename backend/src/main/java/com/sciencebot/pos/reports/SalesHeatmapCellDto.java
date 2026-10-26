package com.sciencebot.pos.reports;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** Ventas de un día de la semana y una hora, para el mapa de calor del Dashboard. */
public record SalesHeatmapCellDto(
        @Schema(description = "Día de la semana ISO: 1 = lunes … 7 = domingo", example = "5")
        int dayOfWeek,
        @Schema(description = "Hora del día (0-23)", example = "17")
        int hourOfDay,
        @Schema(description = "Cantidad de ventas en esa franja", example = "12")
        long transactionCount,
        @Schema(description = "Monto vendido en esa franja", example = "345000.00")
        BigDecimal totalSold
) {}
