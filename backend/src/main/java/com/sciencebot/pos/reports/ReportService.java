package com.sciencebot.pos.reports;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportService {
    SalesReportDto getSalesReport(LocalDateTime dateFrom, LocalDateTime dateTo);
    List<TopProductDto> getTopProductsReport(LocalDateTime dateFrom, LocalDateTime dateTo, int limit);
    List<StockReportDto> getStockReport(Boolean belowMinStock);
    List<ProfitabilityReportDto> getProfitabilityReport(LocalDateTime dateFrom, LocalDateTime dateTo);
    PurchasesReportDto getPurchasesReport(LocalDateTime dateFrom, LocalDateTime dateTo);
    CashClosingReportDto getCashClosingReport(LocalDateTime dateFrom, LocalDateTime dateTo);
}
