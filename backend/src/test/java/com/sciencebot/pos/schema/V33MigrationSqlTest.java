package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Corre toda la cadena Flyway (V1..V33): numeración local y company.id para el proveedor Alegra. */
class V33MigrationSqlTest {

    private static final String URL = "jdbc:h2:mem:v33_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    @Test
    void createsLocalNumberingTablesAndProviderColumns() throws Exception {
        Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").load().migrate();

        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            assertColumns(conn, "BILLING_NUMBERING_RANGE", "ID", "STORE_ID", "ENVIRONMENT", "DOCUMENT", "PREFIX",
                    "RESOLUTION_NUMBER", "MIN_NUMBER", "MAX_NUMBER", "NEXT_NUMBER", "START_DATE", "END_DATE",
                    "TECHNICAL_KEY", "ACTIVE", "DELETED", "CREATED_AT", "UPDATED_AT");
            assertColumns(conn, "BILLING_DOCUMENT_NUMBER", "ID", "STORE_ID", "ENVIRONMENT", "DOCUMENT",
                    "REFERENCE_CODE", "RANGE_ID", "PREFIX", "NUMBER", "IDEMPOTENCY_KEY", "COMPLETED",
                    "CREATED_AT", "UPDATED_AT");
            assertColumns(conn, "STORE_BILLING_CONFIG", "SANDBOX_COMPANY_ID", "PRODUCTION_COMPANY_ID");
            assertColumns(conn, "ELECTRONIC_INVOICES", "PROVIDER_DOCUMENT_ID");
            assertColumns(conn, "ELECTRONIC_CREDIT_NOTES", "PROVIDER_DOCUMENT_ID");
            assertColumns(conn, "ELECTRONIC_DEBIT_NOTES", "PROVIDER_DOCUMENT_ID");
        }
    }

    @Test
    void guardsAgainstDuplicateRangesAndDuplicateNumbers() throws Exception {
        Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").load().migrate();

        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            Set<String> unique = uniqueConstraints(conn);
            assertTrue(unique.contains("UK_BILLING_NUMBERING_RANGE"), "rango unico por local/entorno/documento/prefijo/resolucion: " + unique);
            assertTrue(unique.contains("UK_BILLING_DOCUMENT_NUMBER_REFERENCE"), "una referencia recibe un solo numero: " + unique);
            assertTrue(unique.contains("UK_BILLING_DOCUMENT_NUMBER_RANGE_NUMBER"), "un numero de un rango no se asigna dos veces: " + unique);
        }
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

    private static Set<String> uniqueConstraints(Connection conn) throws Exception {
        Set<String> names = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_TYPE = 'UNIQUE'");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString(1).toUpperCase());
            }
        }
        return names;
    }
}
