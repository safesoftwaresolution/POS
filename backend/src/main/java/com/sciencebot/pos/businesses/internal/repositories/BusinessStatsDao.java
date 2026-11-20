package com.sciencebot.pos.businesses.internal.repositories;

import com.sciencebot.pos.businesses.DailyTotal;
import com.sciencebot.pos.businesses.PaymentMethodTotal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only aggregations over several stores of one business, with native SQL (SUM/COUNT/GROUP BY) like
 * {@code reports} and {@code BackofficeStatsDao}. It is a deliberate exception to "each module touches its own
 * tables": the parent admin's view crosses sales, returns and products of many stores. The store ids always come
 * from the business of the authenticated owner, never from the request, and the period is half-open
 * ({@code from} inclusive, {@code to} exclusive).
 */
@Repository
public class BusinessStatsDao {

    /** Count and amount grouped by store. */
    public record StoreAmount(Long storeId, long count, BigDecimal amount) {}

    private final JdbcClient jdbcClient;

    public BusinessStatsDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<StoreAmount> salesByStore(Collection<Long> storeIds, LocalDateTime from, LocalDateTime to) {
        return jdbcClient.sql("""
                SELECT s.store_id AS storeId, COUNT(s.id) AS c, COALESCE(SUM(s.total_amount), 0) AS a
                FROM sales s
                WHERE s.store_id IN (:storeIds) AND s.created_at >= :from AND s.created_at < :to
                GROUP BY s.store_id
                """)
                .param("storeIds", storeIds).param("from", from).param("to", to)
                .query((rs, i) -> new StoreAmount(rs.getLong("storeId"), rs.getLong("c"), rs.getBigDecimal("a")))
                .list();
    }

    public List<StoreAmount> returnsByStore(Collection<Long> storeIds, LocalDateTime from, LocalDateTime to) {
        return jdbcClient.sql("""
                SELECT r.store_id AS storeId, COUNT(r.id) AS c, COALESCE(SUM(r.total_refund), 0) AS a
                FROM sale_returns r
                WHERE r.store_id IN (:storeIds) AND r.created_at >= :from AND r.created_at < :to
                GROUP BY r.store_id
                """)
                .param("storeIds", storeIds).param("from", from).param("to", to)
                .query((rs, i) -> new StoreAmount(rs.getLong("storeId"), rs.getLong("c"), rs.getBigDecimal("a")))
                .list();
    }

    public List<PaymentMethodTotal> paymentMethods(Collection<Long> storeIds, LocalDateTime from, LocalDateTime to) {
        return jdbcClient.sql("""
                SELECT COALESCE(s.payment_method, 'CASH') AS method, COUNT(s.id) AS c, COALESCE(SUM(s.total_amount), 0) AS a
                FROM sales s
                WHERE s.store_id IN (:storeIds) AND s.created_at >= :from AND s.created_at < :to
                GROUP BY COALESCE(s.payment_method, 'CASH')
                ORDER BY a DESC
                """)
                .param("storeIds", storeIds).param("from", from).param("to", to)
                .query((rs, i) -> new PaymentMethodTotal(rs.getString("method"), rs.getLong("c"), rs.getBigDecimal("a")))
                .list();
    }

    public List<DailyTotal> daily(Collection<Long> storeIds, LocalDateTime from, LocalDateTime to) {
        return jdbcClient.sql("""
                SELECT CAST(s.created_at AS DATE) AS d, COUNT(s.id) AS c, COALESCE(SUM(s.total_amount), 0) AS a
                FROM sales s
                WHERE s.store_id IN (:storeIds) AND s.created_at >= :from AND s.created_at < :to
                GROUP BY CAST(s.created_at AS DATE)
                ORDER BY d
                """)
                .param("storeIds", storeIds).param("from", from).param("to", to)
                .query((rs, i) -> new DailyTotal(rs.getDate("d").toLocalDate(), rs.getLong("c"), rs.getBigDecimal("a")))
                .list();
    }

    /** Active products at or below their minimum stock, per store (a snapshot, not tied to a period). */
    public Map<Long, Long> lowStockByStore(Collection<Long> storeIds) {
        Map<Long, Long> result = new HashMap<>();
        jdbcClient.sql("""
                SELECT p.store_id AS storeId, COUNT(p.id) AS c
                FROM products p
                WHERE p.store_id IN (:storeIds) AND p.active = TRUE AND p.quantity_available <= p.min_stock
                GROUP BY p.store_id
                """)
                .param("storeIds", storeIds)
                .query((rs, i) -> result.put(rs.getLong("storeId"), rs.getLong("c")))
                .list();
        return result;
    }
}
