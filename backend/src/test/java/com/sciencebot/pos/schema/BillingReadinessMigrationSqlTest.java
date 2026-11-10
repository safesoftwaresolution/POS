package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BillingReadinessMigrationSqlTest {
    @Test
    void forwardUpgradePreservesUnknownLegacyReceiptsAndCreatesRegistrationJournal() throws Exception {
        String url = "jdbc:h2:mem:billing_readiness_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("42").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
            statement.execute("INSERT INTO billing_document_number "
                    + "(store_id, environment, document, reference_code, range_id, prefix, number, "
                    + "idempotency_key, completed, created_at, updated_at) VALUES "
                    + "(98, 'SANDBOX', '01', 'LEGACY-RECEIPT', 98, 'QA', 7, 'legacy-key', TRUE, "
                    + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            for (String table : new String[]{"electronic_invoices", "electronic_credit_notes", "electronic_debit_notes"}) {
                try (var result = statement.executeQuery("SELECT environment, provider_name, provider_company_id FROM " + table)) {
                    assertNotNull(result.getMetaData());
                }
            }
            try (var result = statement.executeQuery("SELECT number, idempotency_key, completed, outcome, "
                    + "request_payload, response_payload, provider_document_id, request_hash "
                    + "FROM billing_document_number WHERE reference_code = 'LEGACY-RECEIPT'")) {
                assertTrue(result.next());
                assertEquals(7, result.getLong("number"));
                assertEquals("legacy-key", result.getString("idempotency_key"));
                assertTrue(result.getBoolean("completed"));
                assertNull(result.getString("outcome"), "legacy completion is not evidence of acceptance");
                assertNull(result.getString("response_payload"));
                assertNull(result.getString("request_payload"));
            }
            try (var result = statement.executeQuery("SELECT store_id, environment, provider_name, identification, "
                    + "operation_id, state, provider_company_id, processing_started_at, updated_at "
                    + "FROM billing_company_registration")) {
                assertFalse(result.next());
            }
        }
    }
}
