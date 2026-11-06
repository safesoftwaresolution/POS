package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V37 deja la habilitacion de la facturacion en autoservicio: retira el CHECK de estados de store_billing_config
 * (los estados se validan en Java), lleva DOCUMENTS_SUBMITTED / AWAITING_PROVIDER / REJECTED a IN_SETUP, crea
 * billing_company_profile y agrega a electronic_invoices el entorno con el que se emitio, tomado del local.
 */
class V37MigrationSqlTest {

    @Test
    void mapsTheRetiredStatesToInSetupAndBackfillsTheInvoiceEnvironment() throws Exception {
        String url = "jdbc:h2:mem:v37_migration_data;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("36").load().migrate();

        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement st = conn.createStatement()) {
            // Sin FK ni CHECK ... IN: H2 2.4.x falla al validar ese CHECK desde otra conexion (ver V36MigrationSqlTest).
            // V37 usa DROP CONSTRAINT IF EXISTS, asi que soltarlo aqui no cambia lo que se prueba de los datos.
            st.execute("ALTER TABLE store_billing_config DROP CONSTRAINT CK_store_billing_config_status");
            st.execute("ALTER TABLE store_billing_config DROP CONSTRAINT CK_store_billing_config_environment");
            st.execute("SET REFERENTIAL_INTEGRITY FALSE");
            String[][] stores = {
                    {"1", "NOT_REQUESTED", "SANDBOX"},
                    {"2", "DOCUMENTS_SUBMITTED", "SANDBOX"},
                    {"3", "AWAITING_PROVIDER", "PRODUCTION"},
                    {"4", "ACTIVE", "PRODUCTION"},
                    {"5", "REJECTED", "SANDBOX"},
                    {"6", "SUSPENDED", "SANDBOX"},
            };
            for (String[] store : stores) {
                st.execute("INSERT INTO store_billing_config (store_id, status, environment, created_at, updated_at) "
                        + "VALUES (" + store[0] + ", '" + store[1] + "', '" + store[2] + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            }
            st.execute("INSERT INTO electronic_invoices (sale_id, status, store_id, created_at, updated_at) "
                    + "VALUES (401, 'VALIDATED', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            st.execute("INSERT INTO electronic_invoices (sale_id, status, store_id, created_at, updated_at) "
                    + "VALUES (601, 'ERROR', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            st.execute("INSERT INTO electronic_invoices (sale_id, status, store_id, created_at, updated_at) "
                    + "VALUES (901, 'PENDING', 9, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            st.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }

        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            Map<Long, String> statuses = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT store_id, status FROM store_billing_config");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    statuses.put(rs.getLong(1), rs.getString(2));
                }
            }
            assertEquals(Map.of(
                    1L, "NOT_REQUESTED",
                    2L, "IN_SETUP",
                    3L, "IN_SETUP",
                    4L, "ACTIVE",
                    5L, "IN_SETUP",
                    6L, "SUSPENDED"), statuses);

            assertEquals("PRODUCTION", invoiceEnvironment(conn, 401));
            assertEquals("SANDBOX", invoiceEnvironment(conn, 601));
            assertNull(invoiceEnvironment(conn, 901), "un local sin configuracion no tiene entorno que copiar");
        }
    }

    @Test
    void dropsTheStatusCheckAndCreatesTheFiscalProfileTable() throws Exception {
        String url = "jdbc:h2:mem:v37_migration_schema;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            Set<String> constraints = constraintsOf(conn, "STORE_BILLING_CONFIG");
            assertFalse(constraints.contains("CK_STORE_BILLING_CONFIG_STATUS"),
                    "el CHECK de estados debia eliminarse: " + constraints);

            assertColumns(conn, "BILLING_COMPANY_PROFILE", "STORE_ID", "LEGAL_NAME", "TRADE_NAME", "NIT", "DV",
                    "IDENTIFICATION_TYPE", "ORGANIZATION_TYPE", "REGIME_CODE", "ECONOMIC_ACTIVITY", "EMAIL", "PHONE",
                    "ADDRESS", "CITY_CODE", "DEPARTMENT_CODE", "CREATED_AT", "UPDATED_AT");
            assertColumns(conn, "ELECTRONIC_INVOICES", "ENVIRONMENT");
            Set<String> profileConstraints = constraintsOf(conn, "BILLING_COMPANY_PROFILE");
            assertTrue(profileConstraints.contains("PK_BILLING_COMPANY_PROFILE"), profileConstraints.toString());
            assertTrue(profileConstraints.contains("FK_BILLING_COMPANY_PROFILE_STORE"), profileConstraints.toString());

            try (Statement st = conn.createStatement()) {
                // El CHECK de entorno sigue (no es objeto de V37); se suelta solo por el bug de H2 con CHECK ... IN.
                st.execute("ALTER TABLE store_billing_config DROP CONSTRAINT CK_store_billing_config_environment");
                st.execute("SET REFERENTIAL_INTEGRITY FALSE");
                st.execute("INSERT INTO store_billing_config (store_id, status, environment, created_at, updated_at) "
                        + "VALUES (11, 'IN_SETUP', 'PRODUCTION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                st.execute("INSERT INTO billing_company_profile (store_id, legal_name, nit, dv, organization_type, "
                        + "regime_code, email, address, city_code, department_code, created_at, updated_at) "
                        + "VALUES (11, 'Tienda S.A.S.', '900123456', '7', 1, 'RESPONSABLE_DE_IVA', 'a@b.co', "
                        + "'Calle 1', '05001', '05', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                st.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT c.status, p.identification_type FROM store_billing_config c "
                            + "JOIN billing_company_profile p ON p.store_id = c.store_id WHERE c.store_id = 11");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("IN_SETUP", rs.getString(1), "cualquier estado se acepta: se valida en Java");
                assertEquals("31", rs.getString(2), "el tipo de identificacion por defecto es NIT (31)");
            }
        }
    }

    private static String invoiceEnvironment(Connection conn, long saleId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT environment FROM electronic_invoices WHERE sale_id = ?")) {
            ps.setLong(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "falta la factura de la venta " + saleId);
                return rs.getString(1);
            }
        }
    }

    private static Set<String> constraintsOf(Connection conn, String table) throws Exception {
        Set<String> names = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1).toUpperCase());
                }
            }
        }
        return names;
    }

    private static void assertColumns(Connection conn, String table, String... columns) throws Exception {
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
        for (String column : columns) {
            assertTrue(found.contains(column), table + " debe tener la columna " + column + " (tiene " + found + ")");
        }
    }
}
