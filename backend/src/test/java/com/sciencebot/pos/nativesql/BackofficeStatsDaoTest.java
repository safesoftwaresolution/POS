package com.sciencebot.pos.nativesql;

import com.sciencebot.pos.backoffice.BackofficeStatsDto;
import com.sciencebot.pos.backoffice.BackofficeStatsDto.CountItem;
import com.sciencebot.pos.backoffice.BackofficeStatsDto.MonthPoint;
import com.sciencebot.pos.backoffice.internal.repositories.BackofficeStatsDao;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ejecuta las agregaciones nativas del panel de estadísticas contra el esquema real (Flyway sobre H2) con
 * datos controlados, en su propia base para que los totales sean exactos.
 */
class BackofficeStatsDaoTest {

    private static JdbcClient jdbc;
    private static BackofficeStatsDto stats;
    private static long s1;

    @BeforeAll
    static void seedAndQuery() {
        String url = "jdbc:h2:mem:backoffice_stats_dao_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(new DriverManagerDataSource(url, "sa", ""));

        // H2 2.4.x falla al validar un CHECK ... IN (...) desde otra conexión (ver CLAUDE.md); aquí cada
        // sentencia abre la suya y los CHECK no son lo que se prueba.
        jdbc.sql("ALTER TABLE stores DROP CONSTRAINT IF EXISTS CK_stores_status").update();

        // El local por defecto de V3 existe (ACTIVE, plan PRO, sin usuarios). Se añaden dos más en plan BASIC.
        s1 = insertStore("Stats Store 1", "stats1@test.com", "ACTIVE");
        insertStore("Stats Store 2", "stats2@test.com", "PENDING_VERIFICATION");
        long admin = insertUser("stats_admin", "ADMINISTRATOR", s1);
        insertUser("stats_seller", "SELLER", s1); // 2 usuarios activos = tope del plan BASIC (1 + 1)
        long customer = insertCustomer(s1);

        long firstSale = insertSale("STATS-1", s1, customer, admin, "100.00", LocalDateTime.now());
        insertSale("STATS-2", s1, customer, admin, "50.00", LocalDateTime.now());
        insertSale("STATS-3", s1, customer, admin, "25.00", LocalDateTime.now());
        insertSale("STATS-OLD", s1, customer, admin, "10.00", LocalDateTime.now().minusDays(40));
        jdbc.sql("INSERT INTO electronic_invoices (sale_id, status, store_id, created_at, updated_at) "
                + "VALUES (:sale, 'VALIDATED', :store, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("sale", firstSale).param("store", s1).update();

        stats = new BackofficeStatsDao(jdbc).overview();
    }

    private static long insertStore(String name, String email, String status) {
        jdbc.sql("INSERT INTO stores (name, email, status, email_verified, plan_code, created_at, updated_at) "
                + "VALUES (:n, :e, :s, TRUE, 'BASIC', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("n", name).param("e", email).param("s", status).update();
        return jdbc.sql("SELECT id FROM stores WHERE email = :e").param("e", email).query(Long.class).single();
    }

    private static long insertUser(String username, String role, long storeId) {
        jdbc.sql("INSERT INTO users (username, password, email, full_name, role, active, email_verified, store_id, "
                + "created_at, updated_at) VALUES (:u, 'x', :em, :u, :r, TRUE, TRUE, :sid, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("u", username).param("em", username + "@test.com").param("r", role).param("sid", storeId).update();
        return jdbc.sql("SELECT id FROM users WHERE username = :u").param("u", username).query(Long.class).single();
    }

    private static long insertCustomer(long storeId) {
        jdbc.sql("INSERT INTO customers (full_name, identification, store_id, active, created_at, updated_at) "
                + "VALUES ('Cliente Stats', 'STATS-ID-1', :sid, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("sid", storeId).update();
        return jdbc.sql("SELECT id FROM customers WHERE identification = 'STATS-ID-1'").query(Long.class).single();
    }

    private static long insertSale(String number, long storeId, long customerId, long userId, String total, LocalDateTime at) {
        jdbc.sql("INSERT INTO sales (invoice_number, customer_id, total_amount, cash_received, cash_change, user_id, "
                + "store_id, created_at) VALUES (:n, :c, :t, :t, 0, :u, :s, :at)")
                .param("n", number).param("c", customerId).param("t", new BigDecimal(total))
                .param("u", userId).param("s", storeId).param("at", at).update();
        return jdbc.sql("SELECT id FROM sales WHERE invoice_number = :n").param("n", number).query(Long.class).single();
    }

    private static long countOf(List<CountItem> items, String key) {
        return items.stream().filter(i -> key.equals(i.key())).mapToLong(CountItem::count).findFirst().orElse(0);
    }

    @Test
    void kpis_summarizeStoresUsersAndLast30DaysOfSales() {
        var k = stats.kpis();
        assertEquals(3, k.totalStores());          // por defecto + 2
        assertEquals(2, k.activeStores());         // por defecto + Stats Store 1
        assertEquals(2, k.activeUsers());
        assertEquals(3, k.newStoresLast30Days());
        assertEquals(3, k.salesLast30Days());      // la venta de hace 40 días no cuenta
        assertEquals(0, new BigDecimal("175.00").compareTo(k.salesAmountLast30Days()));
        assertEquals(1, k.storesWithSalesLast30Days());
        assertEquals(1, k.storesAtUserLimit());    // Stats Store 1: 2 de 2
    }

    @Test
    void distributions_groupByStatusPlanRoleAndInvoiceStatus() {
        assertEquals(2, countOf(stats.storesByStatus(), "ACTIVE"));
        assertEquals(1, countOf(stats.storesByStatus(), "PENDING_VERIFICATION"));
        assertEquals(2, countOf(stats.storesByPlan(), "Básico"));
        assertEquals(1, countOf(stats.storesByPlan(), "Pro"));
        assertEquals("Básico", stats.storesByPlan().get(0).key(), "ordenados del plan más bajo al más alto");
        assertEquals(1, countOf(stats.usersByRole(), "ADMINISTRATOR"));
        assertEquals(1, countOf(stats.usersByRole(), "SELLER"));
        assertEquals(1, countOf(stats.invoicesByStatus(), "VALIDATED"));
    }

    @Test
    void monthlySeries_haveTwelveContinuousMonthsWithGapsFilledWithZero() {
        YearMonth now = YearMonth.now();
        for (List<MonthPoint> series : List.of(stats.newStoresByMonth(), stats.salesByMonth())) {
            assertEquals(12, series.size());
            assertEquals(now.minusMonths(11).toString(), series.get(0).month());
            assertEquals(now.toString(), series.get(11).month());
        }

        MonthPoint thisMonth = stats.salesByMonth().get(11);
        assertEquals(3, thisMonth.count());
        assertEquals(0, new BigDecimal("175.00").compareTo(thisMonth.amount()));
        assertEquals(3, stats.newStoresByMonth().get(11).count());
        assertEquals(0, stats.newStoresByMonth().get(0).count());

        YearMonth oldSaleMonth = YearMonth.from(LocalDateTime.now().minusDays(40));
        MonthPoint old = stats.salesByMonth().stream().filter(p -> p.month().equals(oldSaleMonth.toString())).findFirst().orElseThrow();
        assertEquals(1, old.count());
        assertEquals(0, new BigDecimal("10.00").compareTo(old.amount()));
    }

    @Test
    void topStores_rankLast30DaysOfSalesByAmount() {
        assertEquals(1, stats.topStoresBySales().size());
        var top = stats.topStoresBySales().get(0);
        assertEquals(s1, top.storeId());
        assertEquals("Stats Store 1", top.name());
        assertEquals(3, top.sales());
        assertEquals(0, new BigDecimal("175.00").compareTo(top.amount()));
    }
}
