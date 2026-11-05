package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** V35 gives every existing store category its visual theme without touching unknown (custom) categories. */
class V35MigrationSqlTest {

    private static final String URL = "jdbc:h2:mem:v35_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").target(target).load();
    }

    @Test
    void knownCategoriesGetTheirThemeAndCustomOnesStayDefault() throws Exception {
        flyway("34").migrate();
        try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO store_categories (name, description, active, created_at, updated_at) VALUES "
                    + "('Supermercado', 'x', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    + "('Papelería', 'x', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    + "('Boutique', 'x', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    + "('Mi categoría propia', 'x', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        }

        flyway("35").migrate();

        Map<String, String> themes = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, theme FROM store_categories")) {
            while (rs.next()) themes.put(rs.getString(1), rs.getString(2));
        }
        assertEquals("GROCERY", themes.get("Supermercado"));
        assertEquals("STATIONERY", themes.get("Papelería"));
        assertEquals("FASHION", themes.get("Boutique"));
        assertEquals("DEFAULT", themes.get("Mi categoría propia"));
    }
}
