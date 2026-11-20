package com.sciencebot.pos.businesses;

import java.math.BigDecimal;

/** Sales of a period: what was sold, what was returned and what is left. */
public record StatsTotals(
        long salesCount,
        BigDecimal salesAmount,
        BigDecimal averageTicket,
        long returnsCount,
        BigDecimal returnsAmount,
        BigDecimal netAmount
) {}
