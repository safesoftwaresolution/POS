package com.sciencebot.pos.businesses;

public record BranchStatsDto(
        Long storeId,
        String name,
        String status,
        boolean main,
        StatsTotals totals,
        /** Active products at or below their minimum stock right now (not limited to the period). */
        long lowStockProducts
) {}
