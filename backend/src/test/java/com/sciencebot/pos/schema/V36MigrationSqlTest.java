package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V36 retira los restos de Factus del esquema: borra las credenciales cifradas de store_billing_config (el rango de
 * numeracion activo de cada entorno se conserva) y renombra electronic_invoices.factus_number a legal_number sin
 * perder los numeros ya emitidos.
 */
class V36MigrationSqlTest {

    private static final String URL = "jdbc:h2:mem:v36_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    @Test
    void dropsTheFactusCredentialsAndRenamesTheLegalNumberKeepingTheData() throws Exception {
        Flyway before = Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration")
                .target("35").load();
        before.migrate();

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement st = conn.createStatement()) {
            // Sin FK ni CHECK ... IN: H2 2.4.x falla al validar ese CHECK desde otra conexion (ver V9MigrationSqlTest).
            // El dato de prueba solo necesita las filas que la migracion toca.
            st.execute("ALTER TABLE store_billing_config DROP CONSTRAINT CK_store_billing_config_status");
            st.execute("ALTER TABLE store_billing_config DROP CONSTRAINT CK_store_billing_config_environment");
            st.execute("SET REFERENTIAL_INTEGRITY FALSE");
            st.execute("INSERT INTO store_billing_config (store_id, status, environment, "
                    + "sandbox_client_id, sandbox_client_secret, sandbox_username, sandbox_password, sandbox_numbering_range_id, "
                    + "production_client_id, production_client_secret, production_username, production_password, "
                    + "production_numbering_range_id, sandbox_company_id, production_company_id, created_at, updated_at) "
                    + "VALUES (7, 'ACTIVE', 'SANDBOX', 'cid', 'v1:secret', 'user', 'v1:pass', 14, "
                    + "'pcid', 'v1:psecret', 'puser', 'v1:ppass', 15, 'cmp-sbx', 'cmp-prod', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            st.execute("INSERT INTO electronic_invoices (sale_id, factus_number, status, store_id, created_at, updated_at) "
                    + "VALUES (501, 'SETP990000123', 'VALIDATED', 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            st.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }

        Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").load().migrate();

        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            Set<String> config = columns(conn, "STORE_BILLING_CONFIG");
            for (String gone : new String[]{"SANDBOX_CLIENT_ID", "SANDBOX_CLIENT_SECRET", "SANDBOX_USERNAME",
                    "SANDBOX_PASSWORD", "PRODUCTION_CLIENT_ID", "PRODUCTION_CLIENT_SECRET", "PRODUCTION_USERNAME",
                    "PRODUCTION_PASSWORD"}) {
                assertFalse(config.contains(gone), gone + " debia eliminarse: " + config);
            }
            for (String kept : new String[]{"SANDBOX_NUMBERING_RANGE_ID", "PRODUCTION_NUMBERING_RANGE_ID",
                    "SANDBOX_COMPANY_ID", "PRODUCTION_COMPANY_ID", "STATUS", "ENVIRONMENT", "UPDATED_BY"}) {
                assertTrue(config.contains(kept), kept + " debia conservarse: " + config);
            }

            Set<String> invoices = columns(conn, "ELECTRONIC_INVOICES");
            assertFalse(invoices.contains("FACTUS_NUMBER"), "factus_number debia renombrarse: " + invoices);
            assertTrue(invoices.contains("LEGAL_NUMBER"), "legal_number debia existir: " + invoices);

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT sandbox_numbering_range_id, production_numbering_range_id, sandbox_company_id, "
                            + "production_company_id, status FROM store_billing_config WHERE store_id = 7");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(14, rs.getInt(1));
                assertEquals(15, rs.getInt(2));
                assertEquals("cmp-sbx", rs.getString(3));
                assertEquals("cmp-prod", rs.getString(4));
                assertEquals("ACTIVE", rs.getString(5));
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT legal_number, status FROM electronic_invoices WHERE sale_id = 501");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("SETP990000123", rs.getString(1));
                assertEquals("VALIDATED", rs.getString(2));
            }
        }
    }

    private static Set<String> columns(Connection conn, String table) throws Exception {
        Set<String> found = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    found.add(rs.getString(1).toUpperCase());
                }
            }
        }
        return found;
    }
}
