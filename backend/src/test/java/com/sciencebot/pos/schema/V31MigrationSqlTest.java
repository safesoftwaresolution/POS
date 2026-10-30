package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Corre toda la cadena Flyway (V1..V31) y valida las tablas de fiado y el método de pago CREDIT. */
class V31MigrationSqlTest {

    private static final String URL = "jdbc:h2:mem:v31_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    @Test
    void creditTablesHaveTheExpectedShape() throws Exception {
        migrate();
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            for (String column : new String[]{"STORE_ID", "CUSTOMER_ID", "BALANCE", "CREATED_AT", "UPDATED_AT"}) {
                assertEquals("NO", nullable(conn, "CREDIT_ACCOUNTS", column), column + " debería ser NOT NULL");
            }
            assertEquals("YES", nullable(conn, "CREDIT_ACCOUNTS", "CREDIT_LIMIT"), "el cupo es opcional");
            assertEquals("1", query(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_NAME = 'CREDIT_ACCOUNTS' AND CONSTRAINT_NAME = 'UK_CREDIT_ACCOUNTS_STORE_CUSTOMER'"),
                    "una sola cuenta por cliente y local");

            for (String column : new String[]{"STORE_ID", "ACCOUNT_ID", "CUSTOMER_ID", "TYPE", "AMOUNT", "BALANCE_AFTER", "CREATED_AT"}) {
                assertEquals("NO", nullable(conn, "CREDIT_MOVEMENTS", column), column + " debería ser NOT NULL");
            }
            for (String column : new String[]{"SALE_ID", "PAYMENT_METHOD", "DUE_DATE", "NOTE", "USER_ID", "USERNAME"}) {
                assertEquals("YES", nullable(conn, "CREDIT_MOVEMENTS", column), column + " es opcional");
            }
        }
    }

    @Test
    void creditIsOfferedAsAPaymentMethod() throws Exception {
        migrate();
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            assertEquals("Fiado (pago después)", query(conn, "SELECT v.label FROM parameter_values v "
                    + "JOIN parameter_topics t ON t.id = v.topic_id "
                    + "WHERE t.code = 'PAYMENT_METHODS' AND v.code = 'CREDIT'"));
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
