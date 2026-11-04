package com.sciencebot.pos.reports;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.reports.internal.repositories.ReportDao;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.SalesTotalsProjection;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.SellerSaleProjection;
import com.sciencebot.pos.reports.internal.repositories.ReportProjections.SupplierPurchaseProjection;
import com.sciencebot.pos.reports.internal.services.ReportServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportServiceImplTest {

    @Mock
    private ReportDao reportDao;

    @InjectMocks
    private ReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setStoreId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getSalesReport_Success() {
        when(reportDao.findSalesTotals(any(), any(), any()))
                .thenReturn(new SalesTotalsProjection(BigDecimal.valueOf(100.00), 5L));
        when(reportDao.findSalesBySeller(any(), any(), any()))
                .thenReturn(List.of(new SellerSaleProjection("cajero1", BigDecimal.valueOf(100.00))));

        SalesReportDto result = reportService.getSalesReport(LocalDateTime.now().minusDays(5), LocalDateTime.now());

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(100.00), result.totalSold());
        assertEquals(5L, result.transactionCount());
        assertTrue(result.salesBySeller().containsKey("cajero1"));
        assertEquals(BigDecimal.valueOf(100.00), result.salesBySeller().get("cajero1"));
    }

    @Test
    void getTopProductsReport_Success() {
        List<TopProductDto> mockList = List.of(
                new TopProductDto(1L, "Arroz", 50L),
                new TopProductDto(2L, "Frijol", 30L)
        );
        when(reportDao.findTopSellingProducts(any(), any(), any(), eq(10)))
                .thenReturn(mockList);

        List<TopProductDto> result = reportService.getTopProductsReport(null, null, 10);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Arroz", result.get(0).productName());
    }

    @Test
    void getStockReport_Success() {
        List<StockReportDto> mockList = List.of(
                new StockReportDto(1L, "Aceite", 2, 5, "Bajo Stock")
        );
        when(reportDao.findStockReport(any(), eq(true))).thenReturn(mockList);

        List<StockReportDto> result = reportService.getStockReport(true);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Bajo Stock", result.get(0).status());
    }

    @Test
    void getProfitabilityReport_Success() {
        List<ProfitabilityReportDto> mockList = List.of(
                new ProfitabilityReportDto(1L, "Arroz", BigDecimal.valueOf(50000.00))
        );
        when(reportDao.findProfitabilityReport(any(), any(), any())).thenReturn(mockList);

        List<ProfitabilityReportDto> result = reportService.getProfitabilityReport(null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(BigDecimal.valueOf(50000.00), result.get(0).profitAmount());
    }

    @Test
    void getPurchasesReport_Success() {
        when(reportDao.findPurchasesTotal(any(), any(), any())).thenReturn(BigDecimal.valueOf(250000.00));
        when(reportDao.findPurchasesBySupplier(any(), any(), any())).thenReturn(List.of(
                new SupplierPurchaseProjection("Distribuidora ABC", BigDecimal.valueOf(250000.00))
        ));

        PurchasesReportDto result = reportService.getPurchasesReport(null, null);

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(250000.00), result.totalInverted());
        assertEquals(BigDecimal.valueOf(250000.00), result.purchasesBySupplier().get("Distribuidora ABC"));
    }

    @Test
    void getCashClosingReport_Success() {
        when(reportDao.findSalesTotalsByPaymentMethod(any(), any(), any())).thenReturn(List.of(
                new com.sciencebot.pos.reports.internal.repositories.ReportProjections.PaymentMethodTotalProjection("CASH", BigDecimal.valueOf(80000.00)),
                new com.sciencebot.pos.reports.internal.repositories.ReportProjections.PaymentMethodTotalProjection("NEQUI", BigDecimal.valueOf(20000.00))
        ));
        when(reportDao.findCashSalesBySeller(any(), any(), any())).thenReturn(List.of(
                new SellerSaleProjection("cajero1", BigDecimal.valueOf(50000.00)),
                new SellerSaleProjection("cajero2", BigDecimal.valueOf(30000.00))
        ));

        CashClosingReportDto result = reportService.getCashClosingReport(null, null);

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(80000.00), result.expectedCash());
        assertEquals(BigDecimal.valueOf(20000.00), result.totalByPaymentMethod().get("NEQUI"));
        assertEquals(BigDecimal.valueOf(50000.00), result.cashBySeller().get("cajero1"));
    }

    @Test
    void getCashClosingReport_CreditSalesAreNotCashButCashPaymentsAre() {
        var projection = com.sciencebot.pos.reports.internal.repositories.ReportProjections.PaymentMethodTotalProjection.class;
        when(reportDao.findSalesTotalsByPaymentMethod(any(), any(), any())).thenReturn(List.of(
                new com.sciencebot.pos.reports.internal.repositories.ReportProjections.PaymentMethodTotalProjection("CASH", BigDecimal.valueOf(80000.00)),
                new com.sciencebot.pos.reports.internal.repositories.ReportProjections.PaymentMethodTotalProjection("CREDIT", BigDecimal.valueOf(60000.00))
        ));
        when(reportDao.findCashSalesBySeller(any(), any(), any())).thenReturn(List.of(
                new SellerSaleProjection("cajero1", BigDecimal.valueOf(80000.00))
        ));
        when(reportDao.findCreditPaymentsByMethod(any(), any(), any())).thenReturn(List.of(
                new com.sciencebot.pos.reports.internal.repositories.ReportProjections.PaymentMethodTotalProjection("CASH", BigDecimal.valueOf(15000.00)),
                new com.sciencebot.pos.reports.internal.repositories.ReportProjections.PaymentMethodTotalProjection("NEQUI", BigDecimal.valueOf(5000.00))
        ));
        when(reportDao.findCashCreditPaymentsBySeller(any(), any(), any())).thenReturn(List.of(
                new SellerSaleProjection("cajero1", BigDecimal.valueOf(10000.00)),
                new SellerSaleProjection("cajero2", BigDecimal.valueOf(5000.00))
        ));

        CashClosingReportDto result = reportService.getCashClosingReport(null, null);

        assertEquals(0, BigDecimal.valueOf(95000.00).compareTo(result.expectedCash()), "80.000 de ventas + 15.000 de abonos en efectivo; el fiado no cuenta");
        assertEquals(0, BigDecimal.valueOf(90000.00).compareTo(result.cashBySeller().get("cajero1")));
        assertEquals(0, BigDecimal.valueOf(5000.00).compareTo(result.cashBySeller().get("cajero2")));
        assertEquals(0, BigDecimal.valueOf(5000.00).compareTo(result.creditPaymentsByMethod().get("NEQUI")));
    }

    @Test
    void getCashClosingReport_NoCashSales_ExpectedCashIsZero() {
        when(reportDao.findSalesTotalsByPaymentMethod(any(), any(), any())).thenReturn(List.of(
                new com.sciencebot.pos.reports.internal.repositories.ReportProjections.PaymentMethodTotalProjection("NEQUI", BigDecimal.valueOf(20000.00))
        ));
        when(reportDao.findCashSalesBySeller(any(), any(), any())).thenReturn(List.of());

        CashClosingReportDto result = reportService.getCashClosingReport(null, null);

        assertEquals(BigDecimal.ZERO, result.expectedCash());
        assertTrue(result.cashBySeller().isEmpty());
    }
}
