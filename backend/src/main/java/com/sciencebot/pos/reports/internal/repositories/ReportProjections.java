package com.sciencebot.pos.reports.internal.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Proyecciones inmutables para el mapeo directo con JdbcClient.
 */
public final class ReportProjections {

    private ReportProjections() {}

    public record SalesTotalsProjection(
            BigDecimal totalSold,
            long transactionCount
    ) {}

    public record SellerSaleProjection(
            String sellerName,
            BigDecimal totalAmount
    ) {}

    public record SupplierPurchaseProjection(
            String supplierName,
            BigDecimal totalAmount
    ) {}

    public record PaymentMethodTotalProjection(
            String paymentMethod,
            BigDecimal totalAmount
    ) {}

    public record ExpiringProductProjection(
            Long productId,
            String productName,
            String internalCode,
            Long lotId,
            String lotCode,
            LocalDate expirationDate,
            int lotRemaining,
            int quantityAvailable
    ) {}
}
