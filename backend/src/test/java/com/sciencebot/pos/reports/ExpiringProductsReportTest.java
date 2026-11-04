package com.sciencebot.pos.reports;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.products.internal.entities.ProductLot;
import com.sciencebot.pos.products.internal.services.ProductLotAllocator;
import com.sciencebot.pos.reports.internal.repositories.ReportDaoJdbc;
import com.sciencebot.pos.reports.internal.services.ReportServiceImpl;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ejecuta el SQL real de /reports/expiring (una fila por lote, con lo que queda de cada uno repartido FEFO con
 * funciones de ventana) contra el esquema Flyway completo sobre H2: qué lotes entran, el orden, el aislamiento por
 * local, los días y el estado. Incluye la prueba de paridad con {@link ProductLotAllocator}, la otra implementación
 * de la misma regla.
 */
class ExpiringProductsReportTest {

    private static JdbcClient jdbc;
    private static long storeA;
    private static long storeB;
    private static final LocalDate TODAY = LocalDate.now();

    private final ReportServiceImpl service = new ReportServiceImpl(new ReportDaoJdbc(jdbc));

    @BeforeAll
    static void migrateAndSeed() {
        String url = "jdbc:h2:mem:expiring_report_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(new DriverManagerDataSource(url, "sa", ""));

        // H2 2.4.x falla al validar un CHECK ... IN (...) desde otra conexión (ver CLAUDE.md); aquí cada sentencia abre la suya.
        jdbc.sql("ALTER TABLE stores DROP CONSTRAINT IF EXISTS CK_stores_status").update();

        storeA = store("exp-a@test.com");
        storeB = store("exp-b@test.com");
        jdbc.sql("INSERT INTO categories (name, created_at, updated_at) VALUES ('Cat Vencimientos', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)").update();

        // YOGUR: lotes 10 + 10 = 20 con stock 15 -> se vendieron 5, salen del lote que ya venció (queda 5; el otro, 10).
        long yogurt = product(storeA, "YOGUR", 15, true);
        lot(storeA, yogurt, "Y1", 10, TODAY.minusDays(3));
        lot(storeA, yogurt, "Y2", 10, TODAY.plusDays(10));
        // QUESO: un lote de 2 que vence hoy.
        lot(storeA, product(storeA, "QUESO", 2, true), "Q1", 2, TODAY);
        // LECHE: 10 (ya vencido) + 50 (en 60 días) con stock 50 -> el lote vencido se agotó con las ventas.
        long milk = product(storeA, "LECHE", 50, true);
        lot(storeA, milk, "M1", 10, TODAY.minusDays(1));
        lot(storeA, milk, "M2", 50, TODAY.plusDays(60));
        // PAN: lote de 5 pero sin existencias -> todo consumido.
        lot(storeA, product(storeA, "PAN", 0, true), "P1", 5, TODAY.plusDays(2));
        // INACTIVO: aunque tenga lote con unidades, no entra.
        lot(storeA, product(storeA, "INACTIVO", 5, false), "I1", 5, TODAY.plusDays(2));
        // SIN-LOTE: no vence.
        product(storeA, "SIN-LOTE", 9, true);
        // MIXTO: lote de 5 y stock 12 -> 5 del lote y 7 unidades sin fecha (solo cuenta el lote).
        lot(storeA, product(storeA, "MIXTO", 12, true), "X1", 5, TODAY.plusDays(5));
        // EMPATE: dos lotes de 3 el mismo día con stock 4 -> se consumieron 2, del lote de menor id.
        long tie = product(storeA, "EMPATE", 4, true);
        lot(storeA, tie, "E1", 3, TODAY.plusDays(4));
        lot(storeA, tie, "E2", 3, TODAY.plusDays(4));
        // Otro local.
        lot(storeB, product(storeB, "OTRO-LOCAL", 7, true), "O1", 7, TODAY.plusDays(1));
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
    void defaultWindow_listsEachLotWithUnitsOrderedByDate() {
        List<ExpiringProductDto> report = service.getExpiringProductsReport(null);

        assertEquals(List.of("Y1", "Q1", "E1", "E2", "X1", "Y2"), report.stream().map(ExpiringProductDto::lotCode).toList());
        assertEquals(List.of("EXPIRED", "TODAY", "EXPIRING", "EXPIRING", "EXPIRING", "EXPIRING"),
                report.stream().map(ExpiringProductDto::status).toList());
        assertEquals(List.of(-3L, 0L, 4L, 4L, 5L, 10L), report.stream().map(ExpiringProductDto::daysToExpire).toList());
        // Lo que queda de cada lote, no lo que entró: Y1 entró con 10 y quedan 5; E1 entró con 3 y queda 1.
        assertEquals(List.of(5, 2, 1, 3, 5, 10), report.stream().map(ExpiringProductDto::lotRemaining).toList());
        assertEquals(List.of(15, 2, 4, 4, 12, 15), report.stream().map(ExpiringProductDto::quantityAvailable).toList());
    }

    @Test
    void aLotThatSalesExhausted_neverAppears_evenIfItsDateIsPast() {
        List<String> codes = service.getExpiringProductsReport(365).stream().map(ExpiringProductDto::lotCode).toList();

        assertFalse(codes.contains("M1"), "el lote vencido de LECHE se agotó con las ventas");
        assertFalse(codes.contains("P1"), "PAN no tiene existencias");
    }

    @Test
    void excludesInactiveProductsProductsWithoutLotsAndOtherStores() {
        List<String> products = service.getExpiringProductsReport(365).stream().map(ExpiringProductDto::internalCode).toList();

        assertFalse(products.contains("INACTIVO"));
        assertFalse(products.contains("SIN-LOTE"));
        assertFalse(products.contains("OTRO-LOCAL"), "el reporte es de un solo local");
    }

    @Test
    void windowIsClampedBetweenOneAndThreeHundredSixtyFiveDays() {
        // 0 se trata como 1: entra lo vencido y lo de hoy, no lo de 4 días.
        assertEquals(List.of("Y1", "Q1"), service.getExpiringProductsReport(0).stream().map(ExpiringProductDto::lotCode).toList());
        // Un valor enorme se acota a 365: ya alcanza el lote de 60 días.
        assertTrue(service.getExpiringProductsReport(100000).stream().anyMatch(p -> "M2".equals(p.lotCode())));
        // La ventana por defecto (30) no incluye el de 60 días.
        assertFalse(service.getExpiringProductsReport(30).stream().anyMatch(p -> "M2".equals(p.lotCode())));
    }

    @Test
    void otherStoreSeesOnlyItsOwnLots() {
        TenantContext.setStoreId(storeB);

        assertEquals(List.of("O1"), service.getExpiringProductsReport(30).stream().map(ExpiringProductDto::lotCode).toList());
    }

    @Test
    void withoutAnActiveStore_isRejected() {
        TenantContext.clear();

        assertThrows(IllegalStateException.class, () -> service.getExpiringProductsReport(30));
    }

    /** La consulta SQL y ProductLotAllocator (Java) deben repartir exactamente igual cada lote. */
    @Test
    void sqlAndJavaAllocationAgree() {
        Map<Long, Integer> sql = service.getExpiringProductsReport(365).stream()
                .collect(Collectors.toMap(ExpiringProductDto::lotId, ExpiringProductDto::lotRemaining));

        Map<Long, Integer> stockByProduct = jdbc.sql("SELECT id, quantity_available FROM products WHERE store_id = :s AND active = true")
                .param("s", storeA).query((rs, n) -> Map.entry(rs.getLong(1), rs.getInt(2))).list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<Long, List<ProductLot>> lotsByProduct = jdbc.sql("SELECT id, product_id, quantity, expiration_date FROM product_lots WHERE store_id = :s")
                .param("s", storeA).query((rs, n) -> {
                    ProductLot lot = new ProductLot();
                    lot.setId(rs.getLong(1));
                    lot.setProductId(rs.getLong(2));
                    lot.setQuantity(rs.getInt(3));
                    lot.setExpirationDate(rs.getDate(4).toLocalDate());
                    return lot;
                }).list().stream().collect(Collectors.groupingBy(ProductLot::getProductId));

        Map<Long, Integer> java = new java.util.HashMap<>();
        for (Map.Entry<Long, List<ProductLot>> entry : lotsByProduct.entrySet()) {
            Integer stock = stockByProduct.get(entry.getKey());
            if (stock == null) {
                continue; // producto inactivo: el reporte lo excluye
            }
            ProductLotAllocator.allocate(entry.getValue(), stock).remainingByLotId()
                    .forEach((lotId, remaining) -> {
                        if (remaining > 0) {
                            java.put(lotId, remaining);
                        }
                    });
        }

        assertFalse(sql.isEmpty());
        assertEquals(new java.util.TreeMap<>(java), new java.util.TreeMap<>(sql));
    }

    private static long store(String email) {
        jdbc.sql("INSERT INTO stores (name, email, tax_id, status, email_verified, created_at, updated_at) "
                + "VALUES (:e, :e, '900', 'ACTIVE', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)").param("e", email).update();
        return jdbc.sql("SELECT id FROM stores WHERE email = :e").param("e", email).query(Long.class).single();
    }

    private static long product(long storeId, String code, int stock, boolean active) {
        jdbc.sql("INSERT INTO products (internal_code, name, category_id, purchase_price, sale_price, quantity_available, "
                + "active, store_id, created_at, updated_at) VALUES (:c, :c, "
                + "(SELECT id FROM categories WHERE name = 'Cat Vencimientos'), 1, 2, :q, :a, :s, "
                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("c", code).param("q", stock).param("a", active).param("s", storeId).update();
        return jdbc.sql("SELECT id FROM products WHERE internal_code = :c AND store_id = :s")
                .param("c", code).param("s", storeId).query(Long.class).single();
    }

    private static void lot(long storeId, long productId, String lotCode, int quantity, LocalDate expiration) {
        jdbc.sql("INSERT INTO product_lots (store_id, product_id, lot_code, quantity, expiration_date, received_at, created_at) "
                + "VALUES (:s, :p, :c, :q, :d, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("s", storeId).param("p", productId).param("c", lotCode).param("q", quantity).param("d", expiration).update();
    }
}
