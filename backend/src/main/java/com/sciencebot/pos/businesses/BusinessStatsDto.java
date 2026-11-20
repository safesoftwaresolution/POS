package com.sciencebot.pos.businesses;

import java.time.LocalDate;
import java.util.List;

/** Totals of every branch of the business together, for the parent admin. {@code from} and {@code to} are inclusive. */
public record BusinessStatsDto(
        LocalDate from,
        LocalDate to,
        int branchCount,
        StatsTotals totals,
        List<PaymentMethodTotal> byPaymentMethod,
        List<DailyTotal> daily
) {}
