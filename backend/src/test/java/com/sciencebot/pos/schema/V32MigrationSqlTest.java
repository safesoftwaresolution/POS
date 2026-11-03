package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Corre toda la cadena Flyway (V1..V32): el CHECK de pago de las ventas deja pasar el fiado sin efectivo. */
class V32MigrationSqlTest {

    private static final String URL = "jdbc:h2:mem:v32_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    @Test
    void salePaymentCheckExemptsCreditSalesAndKeepsTheCashRule() throws Exception {
        Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").load().migrate();

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT cc.CHECK_CLAUSE FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc "
                     + "WHERE cc.CONSTRAINT_NAME = 'CK_SALE_PAYMENT'")) {
            assertTrue(rs.next(), "el CHECK de pago debe seguir existiendo");
            String clause = rs.getString(1).toUpperCase();
            assertTrue(clause.contains("CREDIT"), "el fiado debe quedar exento: " + clause);
            assertTrue(clause.contains("CASH_RECEIVED"), "el resto conserva la regla de efectivo: " + clause);
            assertEquals(false, rs.next());
        }
    }
}
