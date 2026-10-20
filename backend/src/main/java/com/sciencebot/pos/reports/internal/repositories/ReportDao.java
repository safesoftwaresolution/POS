package com.sciencebot.pos.reports.internal.repositories;

import com.sciencebot.pos.reports.ProfitabilityReportDto;
import com.sciencebot.pos.reports.StockReportDto;
import com.sciencebot.pos.reports.TopProductDto;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.PaymentMethodTotalProjection;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.SalesTotalsProjection;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.SellerSaleProjection;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.SupplierPurchaseProjection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface ReportDao {
    SalesTotalsProjection findSalesTotals(Long storeId, LocalDateTime from, LocalDateTime to);
    List<SellerSaleProjection> findSalesBySeller(Long storeId, LocalDateTime from, LocalDateTime to);
    List<TopProductDto> findTopSellingProducts(Long storeId, LocalDateTime from, LocalDateTime to, int limit);
    List<StockReportDto> findStockReport(Long storeId, Boolean belowMinStock);
    List<ProfitabilityReportDto> findProfitabilityReport(Long storeId, LocalDateTime from, LocalDateTime to);
    BigDecimal findPurchasesTotal(Long storeId, LocalDateTime from, LocalDateTime to);
    List<SupplierPurchaseProjection> findPurchasesBySupplier(Long storeId, LocalDateTime from, LocalDateTime to);
    List<PaymentMethodTotalProjection> findSalesTotalsByPaymentMethod(Long storeId, LocalDateTime from, LocalDateTime to);
    List<SellerSaleProjection> findCashSalesBySeller(Long storeId, LocalDateTime from, LocalDateTime to);
}
