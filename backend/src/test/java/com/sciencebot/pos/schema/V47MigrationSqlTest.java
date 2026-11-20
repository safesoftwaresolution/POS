package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V47 lets each billing document remember the store whose company and ranges it used; existing ones keep NULL. */
class V47MigrationSqlTest {

    private static final String URL = "jdbc:h2:mem:v47_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    private static final String[] TABLES = {"electronic_invoices", "electronic_credit_notes", "electronic_debit_notes"};

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").target(target).load();
    }

    @Test
    void everyDocumentTableGetsABillingStoreColumn_thatIsNullForExistingRows() throws Exception {
        flyway("46").migrate();
        flyway("47").migrate();

        try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
            for (String table : TABLES) {
                try (ResultSet rs = st.executeQuery("SELECT billing_store_id FROM " + table)) {
                    assertEquals(1, rs.getMetaData().getColumnCount(), table);
                    assertTrue(!rs.next(), table + " starts empty in a fresh schema");
                }
            }
        }
    }
}
