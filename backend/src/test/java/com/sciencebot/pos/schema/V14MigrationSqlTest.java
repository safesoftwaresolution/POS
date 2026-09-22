package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Corre toda la cadena Flyway (V1..V14) y valida la tabla store_billing_config. */
class V14MigrationSqlTest {

    @Test
    void storeBillingConfigTableIsCreatedWithExpectedShape() throws Exception {
        String url = "jdbc:h2:mem:v14_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            for (String column : new String[]{"STORE_ID", "STATUS", "ENVIRONMENT", "CREATED_AT", "UPDATED_AT"}) {
                assertEquals("NO", nullable(conn, column), column + " debería ser NOT NULL");
            }
            for (String env : new String[]{"SANDBOX", "PRODUCTION"}) {
                for (String field : new String[]{"CLIENT_ID", "CLIENT_SECRET", "USERNAME", "PASSWORD", "NUMBERING_RANGE_ID"}) {
                    assertEquals("YES", nullable(conn, env + "_" + field));
                }
            }
            assertEquals(1L, count(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_NAME = 'STORE_BILLING_CONFIG' AND CONSTRAINT_TYPE = 'PRIMARY KEY'"));
            assertEquals(1L, count(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_NAME = 'STORE_BILLING_CONFIG' AND CONSTRAINT_TYPE = 'FOREIGN KEY'"));
        }
    }

    private String nullable(Connection conn, String column) throws SQLException {
        String sql = "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'STORE_BILLING_CONFIG' AND COLUMN_NAME = '" + column + "'";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "Columna no encontrada: " + column);
            return rs.getString(1);
        }
    }

    private long count(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }
}
