package com.sciencebot.pos.reports.internal.services;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.reports.*;
import com.sciencebot.pos.reports.internal.repositories.ReportDao;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.SalesTotalsProjection;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.SellerSaleProjection;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.SupplierPurchaseProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private static final int DEFAULT_REPORT_DAYS = 30;
    private static final int DEFAULT_TOP_PRODUCTS_LIMIT = 10;
    private static final int DEFAULT_EXPIRING_DAYS = 30;
    private static final int MAX_EXPIRING_DAYS = 365;
    private static final int MAX_EXPIRING_ROWS = 500;

    private final ReportDao reportDao;

    public ReportServiceImpl(ReportDao reportDao) {
        this.reportDao = reportDao;
    }

    @Override
    public SalesReportDto getSalesReport(LocalDateTime dateFrom, LocalDateTime dateTo) {
        LocalDateTime from = (dateFrom != null) ? dateFrom : LocalDateTime.now().minusDays(DEFAULT_REPORT_DAYS);
        LocalDateTime to = (dateTo != null) ? dateTo : LocalDateTime.now();
        Long storeId = requireCurrentStoreId();

        SalesTotalsProjection totals = reportDao.findSalesTotals(storeId, from, to);
        List<SellerSaleProjection> sellerSales = reportDao.findSalesBySeller(storeId, from, to);

        Map<String, BigDecimal> salesBySeller = new LinkedHashMap<>();
        for (SellerSaleProjection sale : sellerSales) {
            salesBySeller.put(sale.sellerName(), sale.totalAmount());
        }

        return new SalesReportDto(from, to, totals.totalSold(), totals.transactionCount(), salesBySeller);
    }

    @Override
    public List<TopProductDto> getTopProductsReport(LocalDateTime dateFrom, LocalDateTime dateTo, int limit) {
        LocalDateTime from = (dateFrom != null) ? dateFrom : LocalDateTime.now().minusDays(DEFAULT_REPORT_DAYS);
        LocalDateTime to = (dateTo != null) ? dateTo : LocalDateTime.now();
        int maxResults = (limit > 0) ? limit : DEFAULT_TOP_PRODUCTS_LIMIT;

        return reportDao.findTopSellingProducts(requireCurrentStoreId(), from, to, maxResults);
    }

    @Override
    public List<StockReportDto> getStockReport(Boolean belowMinStock) {
        return reportDao.findStockReport(requireCurrentStoreId(), belowMinStock);
    }

    @Override
    public List<ExpiringProductDto> getExpiringProductsReport(Integer days) {
        int window = (days == null) ? DEFAULT_EXPIRING_DAYS : Math.min(Math.max(days, 1), MAX_EXPIRING_DAYS);
        LocalDate today = LocalDate.now();

        return reportDao.findExpiringProducts(requireCurrentStoreId(), today.plusDays(window), MAX_EXPIRING_ROWS).stream()
                .map(row -> {
                    long daysToExpire = ChronoUnit.DAYS.between(today, row.expirationDate());
                    String status = daysToExpire < 0 ? "EXPIRED" : daysToExpire == 0 ? "TODAY" : "EXPIRING";
                    return new ExpiringProductDto(row.productId(), row.productName(), row.internalCode(),
                            row.lotId(), row.lotCode(), row.expirationDate(), daysToExpire, row.lotRemaining(),
                            row.quantityAvailable(), status);
                })
                .toList();
    }

    @Override
    public List<ProfitabilityReportDto> getProfitabilityReport(LocalDateTime dateFrom, LocalDateTime dateTo) {
        LocalDateTime from = (dateFrom != null) ? dateFrom : LocalDateTime.now().minusDays(DEFAULT_REPORT_DAYS);
        LocalDateTime to = (dateTo != null) ? dateTo : LocalDateTime.now();

        return reportDao.findProfitabilityReport(requireCurrentStoreId(), from, to);
    }

    @Override
    public PurchasesReportDto getPurchasesReport(LocalDateTime dateFrom, LocalDateTime dateTo) {
        LocalDateTime from = (dateFrom != null) ? dateFrom : LocalDateTime.now().minusDays(DEFAULT_REPORT_DAYS);
        LocalDateTime to = (dateTo != null) ? dateTo : LocalDateTime.now();
        Long storeId = requireCurrentStoreId();

        BigDecimal totalInverted = reportDao.findPurchasesTotal(storeId, from, to);
        List<SupplierPurchaseProjection> supplierPurchases = reportDao.findPurchasesBySupplier(storeId, from, to);

        Map<String, BigDecimal> purchasesBySupplier = new LinkedHashMap<>();
        for (SupplierPurchaseProjection purchase : supplierPurchases) {
            purchasesBySupplier.put(purchase.supplierName(), purchase.totalAmount());
        }

        return new PurchasesReportDto(from, to, totalInverted, purchasesBySupplier);
    }

    @Override
    public CashClosingReportDto getCashClosingReport(LocalDateTime dateFrom, LocalDateTime dateTo) {
        LocalDateTime from = (dateFrom != null) ? dateFrom : LocalDateTime.now().minusDays(DEFAULT_REPORT_DAYS);
        LocalDateTime to = (dateTo != null) ? dateTo : LocalDateTime.now();
        Long storeId = requireCurrentStoreId();

        Map<String, BigDecimal> totalByPaymentMethod = new LinkedHashMap<>();
        for (var row : reportDao.findSalesTotalsByPaymentMethod(storeId, from, to)) {
            totalByPaymentMethod.put(row.paymentMethod(), row.totalAmount());
        }

        Map<String, BigDecimal> cashBySeller = new LinkedHashMap<>();
        for (SellerSaleProjection row : reportDao.findCashSalesBySeller(storeId, from, to)) {
            cashBySeller.put(row.sellerName(), row.totalAmount());
        }

        BigDecimal expectedCash = totalByPaymentMethod.getOrDefault("CASH", BigDecimal.ZERO);

        return new CashClosingReportDto(from, to, totalByPaymentMethod, expectedCash, cashBySeller);
    }

    private static Long requireCurrentStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeId;
    }
}
