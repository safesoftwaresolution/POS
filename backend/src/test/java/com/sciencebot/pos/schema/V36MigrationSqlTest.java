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
 * V36 turns the single template of a category into a set (store_category_templates) without losing the existing
 * assignment, gives Boutique RETAIL + APPOINTMENTS, and adds the per-store extras table (store_templates).
 */
class V36MigrationSqlTest {

    private static final String URL = "jdbc:h2:mem:v36_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").target(target).load();
    }

    private static Set<String> templatesOf(Statement st, String category) throws Exception {
        Set<String> result = new HashSet<>();
        try (ResultSet rs = st.executeQuery("SELECT t.template FROM store_category_templates t "
                + "JOIN store_categories c ON c.id = t.category_id WHERE c.name = '" + category + "'")) {
            while (rs.next()) result.add(rs.getString(1));
        }
        return result;
    }

    @Test
    void existingTemplatesAreCopiedBoutiqueGetsRetailToo_andTheOldColumnStays() throws Exception {
        flyway("35").migrate();
        try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO store_categories (name, description, active, template, created_at, updated_at) VALUES "
                    + "('Droguería / Farmacia', 'x', TRUE, 'RETAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    + "('Restaurante / Cafetería', 'x', TRUE, 'FOOD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    + "('Barbería', 'x', TRUE, 'APPOINTMENTS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    + "('Boutique', 'x', TRUE, 'APPOINTMENTS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        }

        flyway("36").migrate();

        try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
            assertEquals(Set.of("RETAIL"), templatesOf(st, "Droguería / Farmacia"));
            assertEquals(Set.of("FOOD"), templatesOf(st, "Restaurante / Cafetería"));
            assertEquals(Set.of("APPOINTMENTS"), templatesOf(st, "Barbería"));
            assertEquals(Set.of("RETAIL", "APPOINTMENTS"), templatesOf(st, "Boutique"));

            // Expand/contract: the V33 column is kept (unmapped) so a rollback of the app still works.
            try (ResultSet rs = st.executeQuery("SELECT template FROM store_categories WHERE name = 'Barbería'")) {
                rs.next();
                assertEquals("APPOINTMENTS", rs.getString(1));
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM store_templates")) {
                rs.next();
                assertEquals(0, rs.getInt(1));
            }
        }
    }
}
