package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24 crea electronic_debit_notes. Corre la cadena completa de migraciones y revisa en
 * INFORMATION_SCHEMA las columnas de las que depende ElectronicDebitNote (nulabilidad y default
 * del tipo de operación) y la unicidad global de reference_code.
 */
class V24MigrationSqlTest {

    @Test
    void debitNotesTableIsCreatedWithExpectedColumns() throws Exception {
        String url = "jdbc:h2:mem:v24_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        String columnsSql = "SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'ELECTRONIC_DEBIT_NOTES'";
        String uniqueSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                + "JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME "
                + "WHERE tc.TABLE_NAME = 'ELECTRONIC_DEBIT_NOTES' AND tc.CONSTRAINT_TYPE = 'UNIQUE' "
                + "AND kcu.COLUMN_NAME = 'REFERENCE_CODE'";

        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement st = conn.createStatement()) {

            Map<String, String> nullable = new HashMap<>();
            Map<String, String> defaults = new HashMap<>();
            try (ResultSet rs = st.executeQuery(columnsSql)) {
                while (rs.next()) {
                    nullable.put(rs.getString(1), rs.getString(2));
                    defaults.put(rs.getString(1), rs.getString(3));
                }
            }

            for (String column : new String[]{
                    "ID", "STORE_ID", "SALE_ID", "REFERENCE_CODE", "CORRECTION_CONCEPT_CODE", "CUSTOMIZATION_ID",
                    "BILL_NUMBER", "DEBIT_NOTE_NUMBER", "CUDE", "QR_CODE", "STATUS", "ERROR_MESSAGE", "PDF_URL",
                    "VALIDATED_AT", "CREATED_AT", "UPDATED_AT"}) {
                assertTrue(nullable.containsKey(column), "falta la columna " + column);
            }
            assertEquals("NO", nullable.get("BILL_NUMBER"), "la nota débito siempre referencia una factura");
            assertEquals("YES", nullable.get("SALE_ID"), "sale_id solo existe si se emitió desde una venta");
            assertEquals("YES", nullable.get("DEBIT_NOTE_NUMBER"), "el número legal llega solo tras validar");
            assertTrue(defaults.get("CUSTOMIZATION_ID").contains("30"), "default de customization_id: " + defaults.get("CUSTOMIZATION_ID"));
            assertTrue(defaults.get("STATUS").contains("PENDING"), "default de status: " + defaults.get("STATUS"));

            try (ResultSet rs = st.executeQuery(uniqueSql)) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "reference_code debe ser UNIQUE");
            }
        }
    }
}
