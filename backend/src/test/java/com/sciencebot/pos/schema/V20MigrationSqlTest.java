package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Corre la cadena Flyway (V1..V20) y valida que la auditoría de locales sobreviva al borrado del local:
 * sin FK a stores y con columnas de snapshot.
 */
class V20MigrationSqlTest {

    @Test
    void storeStatusAuditNoLongerDependsOnStoresAndKeepsASnapshot() throws Exception {
        String url = "jdbc:h2:mem:v20_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            assertEquals(0L, count(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_NAME = 'STORE_STATUS_AUDIT' AND CONSTRAINT_TYPE = 'FOREIGN KEY'"));
            for (String column : new String[]{"STORE_NAME", "STORE_EMAIL", "STORE_TAX_ID"}) {
                assertEquals(1L, count(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME = 'STORE_STATUS_AUDIT' AND COLUMN_NAME = '" + column + "'"));
            }

            // Una fila de auditoría ya no necesita que exista el local (p. ej. tras eliminarlo).
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("INSERT INTO store_status_audit (store_id, new_status, store_name, created_at) "
                        + "VALUES (987654, 'DELETED', 'Local borrado', CURRENT_TIMESTAMP)");
            }
            assertEquals(1L, count(conn, "SELECT COUNT(*) FROM store_status_audit WHERE store_id = 987654"));
        }
    }

    private long count(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }
}
