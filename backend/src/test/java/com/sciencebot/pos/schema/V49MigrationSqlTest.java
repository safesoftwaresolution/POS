package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V49 adds nullable commercial prices to plans; limits and existing rows are untouched. */
class V49MigrationSqlTest {

    private static final String URL = "jdbc:h2:mem:v49_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").target(target).load();
    }

    @Test
    void plansGetNullableDecimalPriceColumns_andKeepTheirLimits() throws Exception {
        flyway("48").migrate();
        flyway("49").migrate();

        try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
            for (String column : new String[]{"MONTHLY_PRICE", "YEARLY_PRICE"}) {
                try (ResultSet rs = st.executeQuery("SELECT DATA_TYPE, NUMERIC_PRECISION, NUMERIC_SCALE, IS_NULLABLE "
                        + "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'PLANS' AND COLUMN_NAME = '" + column + "'")) {
                    assertTrue(rs.next(), column + " exists");
                    assertEquals("NUMERIC", rs.getString("DATA_TYPE"), column);
                    assertEquals(12, rs.getInt("NUMERIC_PRECISION"), column);
                    assertEquals(2, rs.getInt("NUMERIC_SCALE"), column);
                    assertEquals("YES", rs.getString("IS_NULLABLE"), column);
                }
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT code, max_additional_users, monthly_price, yearly_price FROM plans ORDER BY sort_order")) {
                int[] expected = {1, 4, 10};
                for (int limit : expected) {
                    assertTrue(rs.next());
                    assertEquals(limit, rs.getInt("max_additional_users"));
                    assertEquals(null, rs.getBigDecimal("monthly_price"));
                    assertEquals(null, rs.getBigDecimal("yearly_price"));
                }
                assertFalse(rs.next());
            }
        }
    }
}
