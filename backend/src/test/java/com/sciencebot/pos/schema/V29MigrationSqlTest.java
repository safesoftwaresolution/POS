package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Corre toda la cadena Flyway (V1..V29) y valida el catálogo de servicios y las líneas de venta de servicio. */
class V29MigrationSqlTest {

    private static final String URL = "jdbc:h2:mem:v29_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    @Test
    void servicesTableAndSaleItemColumnsHaveExpectedShape() throws Exception {
        migrate();
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            for (String column : new String[]{"STORE_ID", "CODE", "NAME", "CATEGORY_ID", "PRICE", "TAX_RATE",
                    "UNIT_MEASURE_CODE", "ACTIVE", "CREATED_AT", "UPDATED_AT"}) {
                assertEquals("NO", nullable(conn, "SERVICES", column), column + " debería ser NOT NULL");
            }
            assertEquals("YES", nullable(conn, "SERVICES", "DURATION_MINUTES"), "la duración es opcional");
            assertEquals("YES", nullable(conn, "SERVICES", "UNSPSC_CODE"));
            assertEquals("1", query(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_NAME = 'SERVICES' AND CONSTRAINT_NAME = 'UK_SERVICES_STORE_CODE'"));

            assertEquals("YES", nullable(conn, "SALE_ITEMS", "PRODUCT_ID"), "una línea de servicio no tiene producto");
            assertEquals("YES", nullable(conn, "SALE_ITEMS", "SERVICE_ID"));
            assertEquals("NO", nullable(conn, "SALE_ITEMS", "ITEM_TYPE"));
            assertEquals("YES", nullable(conn, "SALE_RETURN_ITEMS", "PRODUCT_ID"));
            assertEquals("YES", nullable(conn, "SALE_RETURN_ITEMS", "SERVICE_ID"));
        }
    }

    @Test
    void saleItemMustPointToExactlyOneOfProductOrService() throws Exception {
        migrate();
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            assertEquals("1", query(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_NAME = 'SALE_ITEMS' AND CONSTRAINT_NAME = 'CK_SALE_ITEMS_TARGET'"),
                    "debe existir el CHECK que exige producto XOR servicio");
            String clause = query(conn, "SELECT CHECK_CLAUSE FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS "
                    + "WHERE CONSTRAINT_NAME = 'CK_SALE_ITEMS_TARGET'");
            assertTrue(clause.contains("SERVICE_ID") && clause.contains("PRODUCT_ID"), clause);
        }
    }

    private static void migrate() {
        Flyway.configure()
                .dataSource(URL, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private String nullable(Connection conn, String table, String column) throws SQLException {
        return query(conn, "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'");
    }

    private String query(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "Sin resultados para: " + sql);
            return rs.getString(1);
        }
    }
}
