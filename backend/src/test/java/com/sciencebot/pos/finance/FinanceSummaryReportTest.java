package com.sciencebot.pos.finance;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.finance.internal.mappers.FinanceMapper;
import com.sciencebot.pos.finance.internal.repositories.FinanceReadDao;
import com.sciencebot.pos.finance.internal.services.FinanceServiceImpl;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ejecuta el SQL real de /finance/summary contra el esquema Flyway completo (V48 incluido) sobre H2: ingresos en
 * base caja (fiado fuera, abonos y devoluciones dentro), compras, gastos, costos fijos prorrateados, la comparativa
 * con el período anterior, la serie diaria y el aislamiento por local.
 */
class FinanceSummaryReportTest {

    private static JdbcClient jdbc;
    private static long storeA;
    private static long storeB;
    private static int invoice = 0;

    private final FinanceServiceImpl service = new FinanceServiceImpl(null, null, new FinanceReadDao(jdbc), new FinanceMapper());

    @BeforeAll
    static void migrateAndSeed() {
        String url = "jdbc:h2:mem:finance_summary_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(new DriverManagerDataSource(url, "sa", ""));

        // H2 2.4.x falla al validar un CHECK ... IN (...) desde otra conexión (ver CLAUDE.md).
        jdbc.sql("ALTER TABLE stores DROP CONSTRAINT IF EXISTS CK_stores_status").update();

        storeA = store("fin-a@test.com");
        storeB = store("fin-b@test.com");
        long userA = user(storeA, "fin-user-a");
        long userB = user(storeB, "fin-user-b");
        long customerA = customer(storeA);
        long customerB = customer(storeB);
        long supplierA = supplier(storeA);

        // Período actual: 2026-10-10 a 2026-10-19 (10 días). Anterior: 2026-09-30 a 2026-10-09.
        long cashSale = sale(storeA, userA, customerA, "CASH", "2026-10-10T10:00:00", "100000");
        sale(storeA, userA, customerA, "NEQUI", "2026-10-11T11:00:00", "50000");
        long creditSale = sale(storeA, userA, customerA, "CREDIT", "2026-10-12T12:00:00", "70000");  // no entra dinero
        creditPayment(storeA, customerA, "CASH", "2026-10-15T09:00:00", "30000");                    // abono: sí entra
        refund(storeA, userA, cashSale, "2026-10-16T09:00:00", "20000");                             // devuelve efectivo
        refund(storeA, userA, creditSale, "2026-10-16T10:00:00", "70000");                           // baja deuda: no devuelve dinero
        sale(storeA, userA, customerA, "CASH", "2026-10-25T10:00:00", "999");                        // fuera del período
        sale(storeA, userA, customerA, "CASH", "2026-10-05T10:00:00", "80000");                      // período anterior

        purchase(storeA, userA, supplierA, "2026-10-13T08:00:00", "40000");
        movement(storeA, "EXPENSE", "Servicios", "2026-10-14", "15000");
        movement(storeA, "EXPENSE", "Servicios", "2026-10-15", "5000");
        movement(storeA, "EXPENSE", "Transporte", "2026-10-16", "10000");
        movement(storeA, "EXPENSE", "Servicios", "2026-10-25", "777");                               // fuera del período
        movement(storeA, "INCOME", "Otros ingresos", "2026-10-17", "25000");                         // ingreso manual: suma a ingresos
        movement(storeA, "INCOME", "Otros ingresos", "2026-10-25", "333");                           // fuera del período
        fixedCost(storeA, "Arriendo", "300000", true);
        fixedCost(storeA, "Gimnasio", "999999", false);                                              // inactivo: no cuenta

        sale(storeB, userB, customerB, "CASH", "2026-10-10T10:00:00", "99999");                      // otro local
        movement(storeB, "EXPENSE", "Servicios", "2026-10-14", "88888");
        movement(storeB, "INCOME", "Otros ingresos", "2026-10-14", "55555");
        fixedCost(storeB, "Arriendo", "1", true);
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
    void summary_isCashBasedAndIsolatedByStore() {
        FinanceSummaryDto s = service.getSummary(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 19));

        assertEquals(10, s.days());
        // 100000 + 50000 + 30000 abono + 25000 ingreso manual − 20000 devolución (el fiado no cuenta)
        assertMoney("185000", s.income());
        assertMoney("25000", s.otherIncome());
        assertMoney("40000", s.purchases());
        assertMoney("30000", s.expenses());
        assertMoney("100000", s.fixedCosts()); // 300000 / 30 × 10
        assertMoney("170000", s.totalOutflow());
        assertMoney("15000", s.profit());
    }

    @Test
    void summary_comparesAgainstThePreviousPeriodOfTheSameLength() {
        FinanceSummaryDto s = service.getSummary(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 19));

        // Anterior: ingreso 80000, egreso 100000 (solo costos fijos), utilidad -20000.
        assertMoney("131.3", s.incomeVariationPct());   // (185000 − 80000) / 80000 = 131,25
        assertMoney("70.0", s.outflowVariationPct());
        assertMoney("175.0", s.profitVariationPct());   // (15000 + 20000) / 20000
    }

    @Test
    void summary_breaksDownIncomeByMethodAndExpensesByCategory() {
        FinanceSummaryDto s = service.getSummary(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 19));

        assertEquals(2, s.incomeByPaymentMethod().size());
        assertEquals("CASH", s.incomeByPaymentMethod().get(0).paymentMethod());
        assertMoney("135000", s.incomeByPaymentMethod().get(0).total()); // 100000 + 30000 + 25000 − 20000
        assertEquals("NEQUI", s.incomeByPaymentMethod().get(1).paymentMethod());
        assertMoney("50000", s.incomeByPaymentMethod().get(1).total());

        assertEquals(2, s.expensesByCategory().size());
        assertEquals("Servicios", s.expensesByCategory().get(0).category());
        assertMoney("20000", s.expensesByCategory().get(0).total());
        assertEquals("Transporte", s.expensesByCategory().get(1).category());
    }

    @Test
    void summary_dailySeriesCoversEveryDayWithZeros() {
        FinanceSummaryDto s = service.getSummary(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 19));

        assertEquals(10, s.daily().size());
        assertEquals(LocalDate.of(2026, 10, 10), s.daily().get(0).date());
        assertMoney("100000", s.daily().get(0).income());
        assertMoney("0", s.daily().get(0).outflow());
        assertMoney("40000", s.daily().get(3).outflow());   // 13: compra
        assertMoney("15000", s.daily().get(4).outflow());   // 14: gasto
        assertMoney("-20000", s.daily().get(6).income());   // 16: solo la devolución de efectivo
        assertMoney("25000", s.daily().get(7).income());    // 17: ingreso manual
        assertMoney("0", s.daily().get(9).income());        // 19: sin movimiento
    }

    @Test
    void summary_withoutDataIsAllZeros() {
        FinanceSummaryDto s = service.getSummary(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 3));

        assertMoney("0", s.income());
        assertMoney("0", s.otherIncome());
        assertMoney("0", s.profit().add(s.fixedCosts()));
        assertTrue(s.incomeByPaymentMethod().isEmpty());
        assertEquals(3, s.daily().size());
        assertMoney("0.0", s.incomeVariationPct());
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "esperado " + expected + " pero fue " + actual);
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

    private static long customer(long storeId) {
        jdbc.sql("INSERT INTO customers (full_name, identification, active, store_id, created_at, updated_at) "
                + "VALUES ('Cliente', 'C' || :s, TRUE, :s, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)").param("s", storeId).update();
        return jdbc.sql("SELECT id FROM customers WHERE store_id = :s").param("s", storeId).query(Long.class).single();
    }

    private static long supplier(long storeId) {
        jdbc.sql("INSERT INTO suppliers (store_id, company_name, tax_id, active, created_at, updated_at) "
                + "VALUES (:s, 'Proveedor', 'NIT-1', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)").param("s", storeId).update();
        return jdbc.sql("SELECT id FROM suppliers WHERE store_id = :s").param("s", storeId).query(Long.class).single();
    }

    private static long sale(long storeId, long userId, long customerId, String method, String createdAt, String total) {
        String number = "FIN-" + (++invoice);
        jdbc.sql("INSERT INTO sales (invoice_number, customer_id, total_amount, cash_received, cash_change, user_id, "
                + "store_id, payment_method, created_at) VALUES (:n, :c, :t, :t, 0, :u, :s, :m, :at)")
                .param("n", number).param("c", customerId).param("t", new BigDecimal(total))
                .param("u", userId).param("s", storeId).param("m", method)
                .param("at", LocalDateTime.parse(createdAt)).update();
        return jdbc.sql("SELECT id FROM sales WHERE invoice_number = :n").param("n", number).query(Long.class).single();
    }

    private static void refund(long storeId, long userId, long saleId, String createdAt, String total) {
        jdbc.sql("INSERT INTO sale_returns (sale_id, store_id, user_id, total_refund, created_at) "
                + "VALUES (:sale, :s, :u, :t, :at)")
                .param("sale", saleId).param("s", storeId).param("u", userId)
                .param("t", new BigDecimal(total)).param("at", LocalDateTime.parse(createdAt)).update();
    }

    private static void creditPayment(long storeId, long customerId, String method, String createdAt, String amount) {
        jdbc.sql("INSERT INTO credit_accounts (store_id, customer_id, balance, created_at, updated_at) "
                + "VALUES (:s, :c, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)").param("s", storeId).param("c", customerId).update();
        long accountId = jdbc.sql("SELECT id FROM credit_accounts WHERE store_id = :s AND customer_id = :c")
                .param("s", storeId).param("c", customerId).query(Long.class).single();
        jdbc.sql("INSERT INTO credit_movements (store_id, account_id, customer_id, type, amount, balance_after, "
                + "payment_method, created_at) VALUES (:s, :a, :c, 'PAYMENT', :amt, 0, :m, :at)")
                .param("s", storeId).param("a", accountId).param("c", customerId)
                .param("amt", new BigDecimal(amount)).param("m", method).param("at", LocalDateTime.parse(createdAt)).update();
    }

    private static void purchase(long storeId, long userId, long supplierId, String createdAt, String total) {
        jdbc.sql("INSERT INTO purchases (supplier_id, invoice_number, total_amount, user_id, store_id, created_at) "
                + "VALUES (:sup, NULL, :t, :u, :s, :at)")
                .param("sup", supplierId).param("t", new BigDecimal(total)).param("u", userId)
                .param("s", storeId).param("at", LocalDateTime.parse(createdAt)).update();
    }

    private static void movement(long storeId, String type, String category, String day, String amount) {
        jdbc.sql("INSERT INTO finance_movements (store_id, type, category, amount, payment_method, movement_date, created_at, updated_at) "
                + "VALUES (:s, :t, :cat, :amt, 'CASH', :d, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("s", storeId).param("t", type).param("cat", category).param("amt", new BigDecimal(amount))
                .param("d", LocalDate.parse(day)).update();
    }

    private static void fixedCost(long storeId, String name, String monthly, boolean active) {
        jdbc.sql("INSERT INTO fixed_costs (store_id, name, monthly_amount, active, created_at, updated_at) "
                + "VALUES (:s, :n, :m, :a, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("s", storeId).param("n", name).param("m", new BigDecimal(monthly)).param("a", active).update();
    }
}
