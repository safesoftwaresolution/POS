package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Corre toda la cadena Flyway (V1..V19) y valida la tabla password_reset_tokens. */
class V19MigrationSqlTest {

    @Test
    void passwordResetTokensTableIsCreatedWithExpectedShape() throws Exception {
        String url = "jdbc:h2:mem:v19_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            for (String column : new String[]{"TOKEN_HASH", "USER_ID", "EXPIRES_AT", "CREATED_AT"}) {
                assertEquals("NO", nullable(conn, column), column + " debería ser NOT NULL");
            }
            assertEquals("YES", nullable(conn, "USED_AT"));
            assertEquals(1L, count(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_NAME = 'PASSWORD_RESET_TOKENS' AND CONSTRAINT_TYPE = 'PRIMARY KEY'"));
            assertEquals(1L, count(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_NAME = 'PASSWORD_RESET_TOKENS' AND CONSTRAINT_TYPE = 'UNIQUE'"));
            assertEquals(1L, count(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_NAME = 'PASSWORD_RESET_TOKENS' AND CONSTRAINT_TYPE = 'FOREIGN KEY'"));
        }
    }

    private String nullable(Connection conn, String column) throws SQLException {
        String sql = "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'PASSWORD_RESET_TOKENS' AND COLUMN_NAME = '" + column + "'";
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
