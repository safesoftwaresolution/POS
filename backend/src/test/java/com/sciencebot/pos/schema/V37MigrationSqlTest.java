package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V37 moves services from the product categories to their own catalog: the categories already used by services are
 * copied by name, linked to the store categories of the stores using them, and the services are re-pointed.
 */
class V37MigrationSqlTest {

    private static final String URL = "jdbc:h2:mem:v37_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").target(target).load();
    }

    private static long id(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void servicesKeepTheirCategoryByName_andAreOfferedToTheStoreCategoryThatUsedThem() throws Exception {
        flyway("36").migrate();
        try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
            // H2 2.4.x cannot validate CHECK ... IN (...) from a connection other than the one that created it.
            st.executeUpdate("ALTER TABLE stores DROP CONSTRAINT CK_stores_status");
            st.executeUpdate("INSERT INTO store_categories (name, description, active, template, created_at, updated_at) VALUES "
                    + "('Peluquería', 'x', TRUE, 'APPOINTMENTS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            long storeCategory = id(st, "SELECT id FROM store_categories WHERE name = 'Peluquería'");
            st.executeUpdate("INSERT INTO stores (name, store_category_id, email, status, email_verified, created_at, updated_at) VALUES "
                    + "('Salón Ana', " + storeCategory + ", 'ana@x.co', 'ACTIVE', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            long store = id(st, "SELECT id FROM stores WHERE email = 'ana@x.co'");
            st.executeUpdate("INSERT INTO categories (name, description, created_at, updated_at) VALUES "
                    + "('Corte', 'c', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), ('Bebidas', 'b', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            long corte = id(st, "SELECT id FROM categories WHERE name = 'Corte'");
            st.executeUpdate("INSERT INTO services (store_id, code, name, category_id, price, created_at, updated_at) VALUES "
                    + "(" + store + ", 'S1', 'Corte clásico', " + corte + ", 20000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        }

        flyway("37").migrate();

        try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
            // Only the category a service used is copied; the product-only one is not.
            Set<String> names = new HashSet<>();
            try (ResultSet rs = st.executeQuery("SELECT name FROM service_categories")) {
                while (rs.next()) names.add(rs.getString(1));
            }
            assertEquals(Set.of("Corte"), names);

            try (ResultSet rs = st.executeQuery("SELECT sc.name FROM services s JOIN service_categories sc ON sc.id = s.category_id")) {
                rs.next();
                assertEquals("Corte", rs.getString(1));
            }
            try (ResultSet rs = st.executeQuery("SELECT c.name FROM service_category_store_categories l "
                    + "JOIN store_categories c ON c.id = l.store_category_id")) {
                rs.next();
                assertEquals("Peluquería", rs.getString(1));
            }
        }
    }
}
