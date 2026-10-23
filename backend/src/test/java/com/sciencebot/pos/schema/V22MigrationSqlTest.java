package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Corre la cadena Flyway (V1..V22) y valida los planes sembrados y el plan de cada local. */
class V22MigrationSqlTest {

    @Test
    void plansAreSeededAndEveryStoreHasAPlan() throws Exception {
        String url = "jdbc:h2:mem:v22_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            assertEquals(1L, count(conn, "SELECT max_additional_users FROM plans WHERE code = 'BASIC'"));
            assertEquals(4L, count(conn, "SELECT max_additional_users FROM plans WHERE code = 'STANDARD'"));
            assertEquals(10L, count(conn, "SELECT max_additional_users FROM plans WHERE code = 'PRO'"));
            assertEquals(3L, count(conn, "SELECT COUNT(*) FROM plans"));

            // Los locales existentes (el local por defecto de V3) pasan al plan más alto.
            assertEquals(0L, count(conn, "SELECT COUNT(*) FROM stores WHERE plan_code <> 'PRO'"));
            assertEquals("NO", nullable(conn));
            assertEquals(1L, count(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_NAME = 'STORES' AND CONSTRAINT_NAME = 'FK_STORES_PLAN'"));
        }
    }

    private String nullable(Connection conn) throws SQLException {
        String sql = "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'STORES' AND COLUMN_NAME = 'PLAN_CODE'";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "Columna plan_code no encontrada");
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
