package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** V33 adds the store template to store categories without changing the categories that already exist. */
class V33MigrationSqlTest {

    private static final String URL = "jdbc:h2:mem:v33_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").target(target).load();
    }

    @Test
    void existingCategoriesKeepWorkingAndTheFoodOneGetsTheFoodTemplate() throws Exception {
        flyway("32").migrate();
        try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO store_categories (name, description, active, created_at, updated_at) VALUES "
                    + "('Droguería / Farmacia', 'x', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    + "('Restaurante / Cafetería', 'x', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        }

        flyway("33").migrate();

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, template FROM store_categories ORDER BY name")) {
            assertEquals(true, rs.next());
            assertEquals("Droguería / Farmacia", rs.getString(1));
            assertEquals("RETAIL", rs.getString(2));
            assertEquals(true, rs.next());
            assertEquals("Restaurante / Cafetería", rs.getString(1));
            assertEquals("FOOD", rs.getString(2));
            assertEquals(false, rs.next());
        }
    }
}
