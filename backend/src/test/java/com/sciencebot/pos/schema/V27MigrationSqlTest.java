package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Corre toda la cadena Flyway (V1..V27) y valida la tabla product_lots (lotes de vencimiento). */
class V27MigrationSqlTest {

    @Test
    void productLotsTableIsCreatedWithExpectedShape() throws Exception {
        String url = "jdbc:h2:mem:v27_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            for (String column : new String[]{"STORE_ID", "PRODUCT_ID", "QUANTITY", "EXPIRATION_DATE", "RECEIVED_AT", "CREATED_AT"}) {
                assertEquals("NO", nullable(conn, column), column + " debería ser NOT NULL");
            }
            assertEquals("YES", nullable(conn, "LOT_CODE"), "el código de lote es opcional");
            assertEquals("DATE", query(conn, "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_NAME = 'PRODUCT_LOTS' AND COLUMN_NAME = 'EXPIRATION_DATE'"));
            assertEquals("2", query(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_NAME = 'PRODUCT_LOTS' AND CONSTRAINT_TYPE = 'FOREIGN KEY'"));
            assertEquals("2", query(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEX_COLUMNS "
                    + "WHERE TABLE_NAME = 'PRODUCT_LOTS' AND INDEX_NAME = 'IDX_PRODUCT_LOTS_STORE_EXPIRATION' "
                    + "AND COLUMN_NAME IN ('STORE_ID', 'EXPIRATION_DATE')"));
            assertEquals("1", query(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEX_COLUMNS "
                    + "WHERE TABLE_NAME = 'PRODUCT_LOTS' AND INDEX_NAME = 'IDX_PRODUCT_LOTS_PRODUCT' AND COLUMN_NAME = 'PRODUCT_ID'"));
            assertEquals("0", query(conn, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_NAME = 'PRODUCTS' AND COLUMN_NAME = 'EXPIRATION_DATE'"),
                    "el vencimiento del producto es derivado de los lotes: no hay columna en products");
        }
    }

    private String nullable(Connection conn, String column) throws SQLException {
        return query(conn, "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'PRODUCT_LOTS' AND COLUMN_NAME = '" + column + "'");
    }

    private String query(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "Sin resultados para: " + sql);
            return rs.getString(1);
        }
    }
}
