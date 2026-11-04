package com.sciencebot.pos.reports.internal.repositories;

import com.sciencebot.pos.reports.ProfitabilityReportDto;
import com.sciencebot.pos.reports.SalesHeatmapCellDto;
import com.sciencebot.pos.reports.StockReportDto;
import com.sciencebot.pos.reports.TopProductDto;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.ExpiringProductProjection;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.PaymentMethodTotalProjection;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.SalesTotalsProjection;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.SellerSaleProjection;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.SupplierPurchaseProjection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ReportDao {
    SalesTotalsProjection findSalesTotals(Long storeId, LocalDateTime from, LocalDateTime to);
    List<SellerSaleProjection> findSalesBySeller(Long storeId, LocalDateTime from, LocalDateTime to);
    List<SalesHeatmapCellDto> findSalesHeatmap(Long storeId, LocalDateTime from, LocalDateTime to);
    List<TopProductDto> findTopSellingProducts(Long storeId, LocalDateTime from, LocalDateTime to, int limit);
    List<StockReportDto> findStockReport(Long storeId, Boolean belowMinStock);
    /** Productos activos con stock cuya fecha de vencimiento es {@code limit} o anterior (incluye los ya vencidos). */
    List<ExpiringProductProjection> findExpiringProducts(Long storeId, LocalDate limit, int maxRows);
    List<ProfitabilityReportDto> findProfitabilityReport(Long storeId, LocalDateTime from, LocalDateTime to);
    BigDecimal findPurchasesTotal(Long storeId, LocalDateTime from, LocalDateTime to);
    List<SupplierPurchaseProjection> findPurchasesBySupplier(Long storeId, LocalDateTime from, LocalDateTime to);
    List<PaymentMethodTotalProjection> findSalesTotalsByPaymentMethod(Long storeId, LocalDateTime from, LocalDateTime to);
    List<SellerSaleProjection> findCashSalesBySeller(Long storeId, LocalDateTime from, LocalDateTime to);
    /** Abonos de fiado recibidos en el período, por método de pago. */
    List<PaymentMethodTotalProjection> findCreditPaymentsByMethod(Long storeId, LocalDateTime from, LocalDateTime to);
    /** Abonos de fiado recibidos en efectivo en el período, por quien los cobró. */
    List<SellerSaleProjection> findCashCreditPaymentsBySeller(Long storeId, LocalDateTime from, LocalDateTime to);
}
