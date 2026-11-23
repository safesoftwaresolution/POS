package com.sciencebot.pos.finance;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Estado financiero del período, en base caja")
public record FinanceSummaryDto(
        LocalDate dateFrom,
        LocalDate dateTo,
        @Schema(description = "Días del período, ambos extremos incluidos") int days,
        @Schema(description = "Ventas cobradas + abonos de fiado + otros ingresos − devoluciones") BigDecimal income,
        @Schema(description = "Parte de income que son ingresos manuales (no vienen de una venta del POS)") BigDecimal otherIncome,
        @Schema(description = "Compras a proveedor del período") BigDecimal purchases,
        @Schema(description = "Egresos manuales del período") BigDecimal expenses,
        @Schema(description = "Costos fijos activos prorrateados: mensual ÷ 30 × días") BigDecimal fixedCosts,
        @Schema(description = "purchases + expenses + fixedCosts") BigDecimal totalOutflow,
        @Schema(description = "income − totalOutflow") BigDecimal profit,
        @Schema(description = "Variación % frente al período anterior de igual longitud") BigDecimal incomeVariationPct,
        BigDecimal outflowVariationPct,
        BigDecimal profitVariationPct,
        List<CategoryTotalDto> expensesByCategory,
        List<PaymentMethodIncomeDto> incomeByPaymentMethod,
        List<DailyFlowDto> daily
) {}
