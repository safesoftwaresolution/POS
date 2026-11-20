package com.sciencebot.pos.businesses;

import com.sciencebot.pos.businesses.internal.repositories.BusinessStatsDao;
import com.sciencebot.pos.businesses.internal.repositories.BusinessStatsDao.StoreAmount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The aggregations of a business only see the stores they are given, and only the requested period. */
@SpringBootTest
@Transactional
class BusinessStatsDaoTest {

    private static final long A = 71_001L;
    private static final long B = 71_002L;
    private static final long OTHER = 71_003L;

    private static final LocalDateTime FROM = LocalDate.of(2026, 3, 10).atStartOfDay();
    private static final LocalDateTime TO = LocalDate.of(2026, 3, 13).atStartOfDay(); // exclusive

    @Autowired
    private BusinessStatsDao dao;
    @Autowired
    private JdbcClient jdbc;

    private int seq;

    private void sale(long storeId, String amount, String method, LocalDateTime at) {
        jdbc.sql("INSERT INTO sales (invoice_number, customer_id, total_amount, cash_received, cash_change, user_id, "
                        + "created_at, store_id, payment_method) VALUES (:n, 1, :a, :a, 0, 1, :at, :s, :m)")
                .param("n", "T-" + storeId + "-" + (++seq)).param("a", new BigDecimal(amount)).param("at", at)
                .param("s", storeId).param("m", method).update();
    }

    private void refund(long storeId, String amount, LocalDateTime at) {
        jdbc.sql("INSERT INTO sale_returns (sale_id, store_id, user_id, total_refund, created_at) "
                        + "VALUES (1, :s, 1, :a, :at)")
                .param("s", storeId).param("a", new BigDecimal(amount)).param("at", at).update();
    }

    private void product(long storeId, String code, int quantity, int minStock, boolean active) {
        jdbc.sql("INSERT INTO products (store_id, internal_code, name, category_id, purchase_price, sale_price, "
                        + "quantity_available, min_stock, active, tax_rate, created_at, updated_at) "
                        + "VALUES (:s, :c, :c, 1, 1, 2, :q, :m, :act, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("s", storeId).param("c", code).param("q", quantity).param("m", minStock).param("act", active).update();
    }

    private void seed() {
        LocalDateTime day1 = LocalDate.of(2026, 3, 10).atTime(10, 0);
        LocalDateTime day2 = LocalDate.of(2026, 3, 11).atTime(15, 30);
        sale(A, "100.00", "CASH", day1);
        sale(A, "50.00", "CASH", day1);
        sale(A, "30.00", "CREDIT", day2);
        sale(B, "200.00", "TRANSFER", day2);
        sale(OTHER, "999.00", "CASH", day1);                                       // another business
        sale(A, "7777.00", "CASH", LocalDate.of(2026, 3, 13).atStartOfDay());      // 'to' is exclusive
        sale(A, "8888.00", "CASH", LocalDate.of(2026, 3, 9).atTime(23, 59));       // before the period
        refund(A, "20.00", day2);
        refund(OTHER, "5.00", day2);
        product(A, "A-LOW-1", 1, 5, true);
        product(A, "A-LOW-2", 5, 5, true);
        product(A, "A-OFF", 0, 5, false);   // inactive: ignored
        product(A, "A-OK", 50, 5, true);
        product(B, "B-OK", 9, 2, true);
        product(OTHER, "O-LOW", 0, 5, true);
    }

    private static Map<Long, StoreAmount> byStore(List<StoreAmount> rows) {
        return rows.stream().collect(java.util.stream.Collectors.toMap(StoreAmount::storeId, r -> r));
    }

    @Test
    void salesAndReturns_areGroupedPerStore_onlyForTheGivenStoresAndPeriod() {
        seed();

        Map<Long, StoreAmount> sales = byStore(dao.salesByStore(List.of(A, B), FROM, TO));
        assertEquals(2, sales.size(), "the other business never shows up");
        assertEquals(3, sales.get(A).count());
        assertEquals(0, new BigDecimal("180.00").compareTo(sales.get(A).amount()));
        assertEquals(1, sales.get(B).count());
        assertEquals(0, new BigDecimal("200.00").compareTo(sales.get(B).amount()));

        Map<Long, StoreAmount> returns = byStore(dao.returnsByStore(List.of(A, B), FROM, TO));
        assertEquals(1, returns.size());
        assertEquals(0, new BigDecimal("20.00").compareTo(returns.get(A).amount()));
    }

    @Test
    void paymentMethods_andDaily_addUpAllTheGivenStores() {
        seed();

        var methods = dao.paymentMethods(List.of(A, B), FROM, TO);
        assertEquals(List.of("TRANSFER", "CASH", "CREDIT"), methods.stream().map(m -> m.paymentMethod()).toList(),
                "ordered by amount, highest first");
        assertEquals(2, methods.get(1).salesCount());

        var daily = dao.daily(List.of(A, B), FROM, TO);
        assertEquals(List.of(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11)), daily.stream().map(d -> d.date()).toList());
        assertEquals(0, new BigDecimal("150.00").compareTo(daily.get(0).amount()));
        assertEquals(0, new BigDecimal("230.00").compareTo(daily.get(1).amount()));
    }

    @Test
    void lowStock_countsOnlyActiveProductsAtOrBelowTheirMinimum() {
        seed();

        Map<Long, Long> low = dao.lowStockByStore(List.of(A, B));

        assertEquals(2L, low.get(A));
        assertNull(low.get(B));
        assertFalse(low.containsKey(OTHER));
    }

    @Test
    void anEmptyPeriodReturnsNothing() {
        seed();
        LocalDateTime later = LocalDate.of(2027, 1, 1).atStartOfDay();

        assertTrue(dao.salesByStore(List.of(A, B), later, later.plusDays(1)).isEmpty());
        assertTrue(dao.daily(List.of(A, B), later, later.plusDays(1)).isEmpty());
    }
}
