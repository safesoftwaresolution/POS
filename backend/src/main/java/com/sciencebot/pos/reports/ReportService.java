package com.sciencebot.pos.reports;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportService {
    SalesReportDto getSalesReport(LocalDateTime dateFrom, LocalDateTime dateTo);
    /** Ventas agrupadas por día de la semana y hora (solo las franjas con ventas); sin fechas, los últimos 30 días. */
    List<SalesHeatmapCellDto> getSalesHeatmap(LocalDateTime dateFrom, LocalDateTime dateTo);
    List<TopProductDto> getTopProductsReport(LocalDateTime dateFrom, LocalDateTime dateTo, int limit);
    List<StockReportDto> getStockReport(Boolean belowMinStock);
    /** Productos vencidos y los que vencen dentro de {@code days} días (por defecto 30, entre 1 y 365). */
    List<ExpiringProductDto> getExpiringProductsReport(Integer days);
    /** El mismo informe de vencimientos como PDF listo para descargar. */
    byte[] getExpiringProductsReportPdf(Integer days);
    List<ProfitabilityReportDto> getProfitabilityReport(LocalDateTime dateFrom, LocalDateTime dateTo);
    PurchasesReportDto getPurchasesReport(LocalDateTime dateFrom, LocalDateTime dateTo);
    CashClosingReportDto getCashClosingReport(LocalDateTime dateFrom, LocalDateTime dateTo);
}
