package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Corre toda la cadena Flyway (V1..V30) y valida el dueño de los clientes y la bitácora de auditoría. */
class V30MigrationSqlTest {

    private static final String URL = "jdbc:h2:mem:v30_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    @Test
    void customersGainOptionalOwnershipColumns() throws Exception {
        migrate();
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            for (String column : new String[]{"CREATED_BY", "CREATED_BY_USERNAME", "UPDATED_BY", "UPDATED_BY_USERNAME"}) {
                assertEquals("YES", nullable(conn, "CUSTOMERS", column),
                        column + " debe admitir NULL: los clientes anteriores no tienen creador");
            }
        }
    }

    @Test
    void auditLogHasTheExpectedShape() throws Exception {
        migrate();
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            for (String column : new String[]{"STORE_ID", "ENTITY_TYPE", "ENTITY_ID", "ACTION", "CREATED_AT"}) {
                assertEquals("NO", nullable(conn, "AUDIT_LOG", column), column + " debería ser NOT NULL");
            }
            assertEquals("YES", nullable(conn, "AUDIT_LOG", "USER_ID"), "el usuario puede no identificarse");
            assertEquals("YES", nullable(conn, "AUDIT_LOG", "CHANGES"), "una baja no lleva campos modificados");
            assertEquals("1", query(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
                    + "WHERE TABLE_NAME = 'AUDIT_LOG' AND INDEX_NAME = 'IDX_AUDIT_LOG_ENTITY'"));
        }
    }

    private static void migrate() {
        Flyway.configure()
                .dataSource(URL, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static String nullable(Connection conn, String table, String column) throws SQLException {
        return query(conn, "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'");
    }

    private static String query(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
