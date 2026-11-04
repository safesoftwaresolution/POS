package com.sciencebot.pos.reports;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.reports.internal.repositories.ReportDaoJdbc;
import com.sciencebot.pos.reports.internal.services.ReportServiceImpl;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ejecuta el SQL real de /reports/sales-heatmap contra el esquema Flyway completo sobre H2: el día de la semana ISO
 * (1 = lunes), la hora, la agrupación, el filtro por fechas y el aislamiento por local.
 */
class SalesHeatmapReportTest {

    private static JdbcClient jdbc;
    private static long storeA;
    private static long storeB;
    private static int invoice = 0;

    private final ReportServiceImpl service = new ReportServiceImpl(new ReportDaoJdbc(jdbc));

    @BeforeAll
    static void migrateAndSeed() {
        String url = "jdbc:h2:mem:heatmap_report_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(new DriverManagerDataSource(url, "sa", ""));

        // H2 2.4.x falla al validar un CHECK ... IN (...) desde otra conexión (ver CLAUDE.md); aquí cada sentencia abre la suya.
        jdbc.sql("ALTER TABLE stores DROP CONSTRAINT IF EXISTS CK_stores_status").update();

        storeA = store("heat-a@test.com");
        storeB = store("heat-b@test.com");
        long userA = user(storeA, "heat-user-a");
        long userB = user(storeB, "heat-user-b");
        long customerA = customer(storeA, "1");
        long customerB = customer(storeB, "2");

        // 2026-10-19 es lunes, 2026-10-23 viernes y 2026-10-25 domingo.
        sale(storeA, userA, customerA, "2026-10-19T09:10:00", "10000");
        sale(storeA, userA, customerA, "2026-10-19T09:50:00", "5000");   // misma franja: lunes 9
        sale(storeA, userA, customerA, "2026-10-19T17:00:00", "20000");  // lunes 17
        sale(storeA, userA, customerA, "2026-10-26T09:30:00", "7000");   // otro lunes, también 9
        sale(storeA, userA, customerA, "2026-10-23T23:59:00", "30000");  // viernes 23
        sale(storeA, userA, customerA, "2026-10-25T00:05:00", "1000");   // domingo 0
        sale(storeB, userB, customerB, "2026-10-19T09:15:00", "99999");  // otro local
    }

    @BeforeEach
    void tenant() {
        TenantContext.setStoreId(storeA);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void groupsByIsoWeekdayAndHour_sortedAndIsolatedByStore() {
        List<SalesHeatmapCellDto> cells = service.getSalesHeatmap(
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 31, 23, 59));

        assertEquals(
                List.of("1-9", "1-17", "5-23", "7-0"),
                cells.stream().map(c -> c.dayOfWeek() + "-" + c.hourOfDay()).toList());

        SalesHeatmapCellDto mondayNine = cells.get(0);
        assertEquals(3, mondayNine.transactionCount());
        assertEquals(0, new BigDecimal("22000").compareTo(mondayNine.totalSold()));
        assertTrue(cells.stream().noneMatch(c -> c.totalSold().compareTo(new BigDecimal("99999")) == 0),
                "las ventas de otro local no deben aparecer");
    }

    @Test
    void dateRange_leavesOutTheSalesOutsideIt() {
        List<SalesHeatmapCellDto> cells = service.getSalesHeatmap(
                LocalDateTime.of(2026, 10, 20, 0, 0), LocalDateTime.of(2026, 10, 24, 23, 59));

        assertEquals(1, cells.size());
        assertEquals(5, cells.get(0).dayOfWeek());
        assertEquals(23, cells.get(0).hourOfDay());
    }

    @Test
    void noSalesInRange_returnsAnEmptyList() {
        assertTrue(service.getSalesHeatmap(
                LocalDateTime.of(2020, 1, 1, 0, 0), LocalDateTime.of(2020, 1, 2, 0, 0)).isEmpty());
    }

    private static long store(String email) {
        jdbc.sql("INSERT INTO stores (name, email, tax_id, status, email_verified, created_at, updated_at) "
                + "VALUES (:e, :e, '900', 'ACTIVE', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)").param("e", email).update();
        return jdbc.sql("SELECT id FROM stores WHERE email = :e").param("e", email).query(Long.class).single();
    }

    private static long user(long storeId, String username) {
        jdbc.sql("INSERT INTO users (username, password, email, full_name, role, active, store_id, created_at, updated_at) "
                + "VALUES (:u, 'x', :u, :u, 'SELLER', TRUE, :s, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("u", username).param("s", storeId).update();
        return jdbc.sql("SELECT id FROM users WHERE username = :u").param("u", username).query(Long.class).single();
    }

    private static long customer(long storeId, String identification) {
        jdbc.sql("INSERT INTO customers (full_name, identification, active, store_id, created_at, updated_at) "
                + "VALUES ('Cliente', :i || :s, TRUE, :s, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("i", identification).param("s", storeId).update();
        return jdbc.sql("SELECT id FROM customers WHERE store_id = :s").param("s", storeId).query(Long.class).single();
    }

    private static void sale(long storeId, long userId, long customerId, String createdAt, String total) {
        jdbc.sql("INSERT INTO sales (invoice_number, customer_id, total_amount, cash_received, cash_change, user_id, "
                + "store_id, created_at) VALUES (:n, :c, :t, :t, 0, :u, :s, :at)")
                .param("n", "HEAT-" + (++invoice)).param("c", customerId).param("t", new BigDecimal(total))
                .param("u", userId).param("s", storeId).param("at", LocalDateTime.parse(createdAt)).update();
    }
}
