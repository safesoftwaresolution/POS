package com.sciencebot.pos.backoffice;

import java.math.BigDecimal;
import java.util.List;

/** Panorama general de la plataforma para el SUPER_ADMIN: indicadores, distribuciones y series mensuales. */
public record BackofficeStatsDto(
    Kpis kpis,
    List<CountItem> storesByStatus,
    List<CountItem> storesByPlan,
    List<CountItem> usersByRole,
    List<CountItem> invoicesByStatus,
    /** Locales creados por mes, de los últimos 12 meses (los meses sin datos vienen en 0). */
    List<MonthPoint> newStoresByMonth,
    /** Ventas de todos los locales por mes, de los últimos 12 meses (cantidad e importe). */
    List<MonthPoint> salesByMonth,
    /** Locales con más ventas en los últimos 30 días. */
    List<TopStore> topStoresBySales
) {

    public record Kpis(
        long totalStores,
        long activeStores,
        long activeUsers,
        long newStoresLast30Days,
        long salesLast30Days,
        BigDecimal salesAmountLast30Days,
        long storesWithSalesLast30Days,
        long storesAtUserLimit
    ) {}

    public record CountItem(String key, long count) {}

    /** {@code month} en formato {@code yyyy-MM}. */
    public record MonthPoint(String month, long count, BigDecimal amount) {}

    public record TopStore(Long storeId, String name, long sales, BigDecimal amount) {}
}
