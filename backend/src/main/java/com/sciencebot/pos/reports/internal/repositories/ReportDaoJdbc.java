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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ReportDaoJdbc implements ReportDao {

    private final JdbcClient jdbcClient;

    public ReportDaoJdbc(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public SalesTotalsProjection findSalesTotals(Long storeId, LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT
                COALESCE(SUM(s.total_amount), 0) AS totalSold,
                COUNT(s.id) AS transactionCount
            FROM sales s
            WHERE s.store_id = :storeId AND s.created_at >= :from AND s.created_at <= :to
            """;

        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("from", from)
                .param("to", to)
                .query(SalesTotalsProjection.class)
                .single();
    }

    @Override
    public List<SellerSaleProjection> findSalesBySeller(Long storeId, LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT
                COALESCE(u.username, 'Desconocido') AS sellerName,
                COALESCE(SUM(s.total_amount), 0) AS totalAmount
            FROM sales s
            LEFT JOIN users u ON u.id = s.user_id
            WHERE s.store_id = :storeId AND s.created_at >= :from AND s.created_at <= :to
            GROUP BY u.username
            ORDER BY totalAmount DESC
            """;

        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("from", from)
                .param("to", to)
                .query(SellerSaleProjection.class)
                .list();
    }

    @Override
    public List<SalesHeatmapCellDto> findSalesHeatmap(Long storeId, LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT
                EXTRACT(ISODOW FROM s.created_at) AS dayOfWeek,
                EXTRACT(HOUR FROM s.created_at) AS hourOfDay,
                COUNT(s.id) AS transactionCount,
                COALESCE(SUM(s.total_amount), 0) AS totalSold
            FROM sales s
            WHERE s.store_id = :storeId AND s.created_at >= :from AND s.created_at <= :to
            GROUP BY EXTRACT(ISODOW FROM s.created_at), EXTRACT(HOUR FROM s.created_at)
            ORDER BY dayOfWeek, hourOfDay
            """;

        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("from", from)
                .param("to", to)
                .query(SalesHeatmapCellDto.class)
                .list();
    }

    @Override
    public List<TopProductDto> findTopSellingProducts(Long storeId, LocalDateTime from, LocalDateTime to, int limit) {
        String sql = """
            SELECT
                p.id AS productId,
                p.name AS productName,
                COALESCE(SUM(si.quantity), 0) AS quantitySold
            FROM sale_items si
            JOIN sales s ON s.id = si.sale_id
            JOIN products p ON p.id = si.product_id
            WHERE s.store_id = :storeId AND s.created_at >= :from AND s.created_at <= :to
            GROUP BY p.id, p.name
            ORDER BY quantitySold DESC
            LIMIT :limit
            """;

        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("from", from)
                .param("to", to)
                .param("limit", limit)
                .query(TopProductDto.class)
                .list();
    }

    @Override
    public List<StockReportDto> findStockReport(Long storeId, Boolean belowMinStock) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                p.id AS productId,
                p.name AS productName,
                p.quantity_available AS quantityAvailable,
                p.min_stock AS minStock,
                CASE
                    WHEN p.quantity_available <= p.min_stock THEN 'Bajo Stock'
                    ELSE 'OK'
                END AS status
            FROM products p
            WHERE p.store_id = :storeId AND p.active = true
            """);

        if (Boolean.TRUE.equals(belowMinStock)) {
            sql.append(" AND p.quantity_available <= p.min_stock");
        }

        sql.append(" ORDER BY p.name ASC");

        return jdbcClient.sql(sql.toString())
                .param("storeId", storeId)
                .query(StockReportDto.class)
                .list();
    }

    @Override
    public List<ExpiringProductProjection> findExpiringProducts(Long storeId, LocalDate limit, int maxRows) {
        // Lo que queda de cada lote no se guarda: se deriva del stock del producto asumiendo que se vende primero lo
        // que vence primero. Consumido = max(0, total de lotes - stock); sale del lote que vence antes (empate por id).
        // "prior" = unidades de los lotes anteriores en ese orden. Misma regla que ProductLotAllocator (hay un test de paridad).
        String sql = """
            SELECT * FROM (
                SELECT
                    p.id AS productId,
                    p.name AS productName,
                    p.internal_code AS internalCode,
                    l.id AS lotId,
                    l.lot_code AS lotCode,
                    l.expiration_date AS expirationDate,
                    p.quantity_available AS quantityAvailable,
                    l.quantity - LEAST(l.quantity, GREATEST(0,
                        GREATEST(0, SUM(l.quantity) OVER (PARTITION BY l.product_id) - p.quantity_available)
                        - (SUM(l.quantity) OVER (PARTITION BY l.product_id ORDER BY l.expiration_date, l.id) - l.quantity)
                    )) AS lotRemaining
                FROM product_lots l
                JOIN products p ON p.id = l.product_id
                WHERE l.store_id = :storeId AND p.store_id = :storeId AND p.active = true
            ) lots
            WHERE lotRemaining > 0 AND expirationDate <= :limit
            ORDER BY expirationDate ASC, productName ASC, lotId ASC
            LIMIT :maxRows
            """;

        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("limit", limit)
                .param("maxRows", maxRows)
                .query(ExpiringProductProjection.class)
                .list();
    }

    @Override
    public List<ProfitabilityReportDto> findProfitabilityReport(Long storeId, LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT
                p.id AS productId,
                p.name AS productName,
                COALESCE(SUM((p.sale_price - p.purchase_price) * si.quantity), 0) AS profitAmount
            FROM sale_items si
            JOIN sales s ON s.id = si.sale_id
            JOIN products p ON p.id = si.product_id
            WHERE s.store_id = :storeId AND s.created_at >= :from AND s.created_at <= :to
            GROUP BY p.id, p.name, p.sale_price, p.purchase_price
            ORDER BY profitAmount DESC
            """;

        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("from", from)
                .param("to", to)
                .query(ProfitabilityReportDto.class)
                .list();
    }

    @Override
    public BigDecimal findPurchasesTotal(Long storeId, LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT COALESCE(SUM(p.total_amount), 0)
            FROM purchases p
            WHERE p.store_id = :storeId AND p.created_at >= :from AND p.created_at <= :to
            """;

        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("from", from)
                .param("to", to)
                .query(BigDecimal.class)
                .single();
    }

    @Override
    public List<SupplierPurchaseProjection> findPurchasesBySupplier(Long storeId, LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT
                COALESCE(sup.company_name, 'Proveedor Desconocido') AS supplierName,
                COALESCE(SUM(p.total_amount), 0) AS totalAmount
            FROM purchases p
            LEFT JOIN suppliers sup ON sup.id = p.supplier_id
            WHERE p.store_id = :storeId AND p.created_at >= :from AND p.created_at <= :to
            GROUP BY sup.company_name
            ORDER BY totalAmount DESC
            """;

        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("from", from)
                .param("to", to)
                .query(SupplierPurchaseProjection.class)
                .list();
    }

    @Override
    public List<PaymentMethodTotalProjection> findSalesTotalsByPaymentMethod(Long storeId, LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT
                s.payment_method AS paymentMethod,
                COALESCE(SUM(s.total_amount), 0) AS totalAmount
            FROM sales s
            WHERE s.store_id = :storeId AND s.created_at >= :from AND s.created_at <= :to
            GROUP BY s.payment_method
            ORDER BY totalAmount DESC
            """;

        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("from", from)
                .param("to", to)
                .query(PaymentMethodTotalProjection.class)
                .list();
    }

    @Override
    public List<SellerSaleProjection> findCashSalesBySeller(Long storeId, LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT
                COALESCE(u.username, 'Desconocido') AS sellerName,
                COALESCE(SUM(s.total_amount), 0) AS totalAmount
            FROM sales s
            LEFT JOIN users u ON u.id = s.user_id
            WHERE s.store_id = :storeId AND s.payment_method = 'CASH'
                AND s.created_at >= :from AND s.created_at <= :to
            GROUP BY u.username
            ORDER BY totalAmount DESC
            """;

        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("from", from)
                .param("to", to)
                .query(SellerSaleProjection.class)
                .list();
    }

    @Override
    public List<PaymentMethodTotalProjection> findCreditPaymentsByMethod(Long storeId, LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT
                COALESCE(m.payment_method, 'CASH') AS paymentMethod,
                COALESCE(SUM(m.amount), 0) AS totalAmount
            FROM credit_movements m
            WHERE m.store_id = :storeId AND m.type = 'PAYMENT'
                AND m.created_at >= :from AND m.created_at <= :to
            GROUP BY COALESCE(m.payment_method, 'CASH')
            ORDER BY totalAmount DESC
            """;

        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("from", from)
                .param("to", to)
                .query(PaymentMethodTotalProjection.class)
                .list();
    }

    @Override
    public List<SellerSaleProjection> findCashCreditPaymentsBySeller(Long storeId, LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT
                COALESCE(m.username, 'Desconocido') AS sellerName,
                COALESCE(SUM(m.amount), 0) AS totalAmount
            FROM credit_movements m
            WHERE m.store_id = :storeId AND m.type = 'PAYMENT'
                AND COALESCE(m.payment_method, 'CASH') = 'CASH'
                AND m.created_at >= :from AND m.created_at <= :to
            GROUP BY m.username
            ORDER BY totalAmount DESC
            """;

        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("from", from)
                .param("to", to)
                .query(SellerSaleProjection.class)
                .list();
    }
}
