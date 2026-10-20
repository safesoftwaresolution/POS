package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Corre la cadena completa de migraciones Flyway (V1..V9) sobre una H2 en memoria (modo
 * PostgreSQL), tal como corre en el perfil 'dev' real. Valida que V9 aplique limpiamente
 * sobre el esquema que dejan las migraciones anteriores y que el aislamiento por local
 * (store_id) quede correctamente reforzado a nivel de base de datos.
 *
 * Nota: las aserciones se hacen por introspeccion de INFORMATION_SCHEMA en lugar de INSERTs
 * reales contra 'stores', porque H2 2.4.x tiene un bug conocido con el CHECK ... IN(...) de
 * esa tabla cuando la conexion que creo la restriccion (la de Flyway) ya se cerro.
 */
class V9MigrationSqlTest {

    @Test
    void allMigrationsApplyCleanlyAndEnforceStoreScoping() throws Exception {
        String url = "jdbc:h2:mem:v9_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            // El local por defecto sembrado en V3 existe y sirve de ancla para el backfill.
            long defaultStoreId = longValue(conn, "SELECT id FROM stores WHERE email = 'default@platform.internal'");
            assertTrue(defaultStoreId > 0);

            // sales/purchases/inventory_movements/products/customers/suppliers ahora exigen store_id.
            assertColumnNotNull(conn, "SALES", "STORE_ID");
            assertColumnNotNull(conn, "PURCHASES", "STORE_ID");
            assertColumnNotNull(conn, "INVENTORY_MOVEMENTS", "STORE_ID");
            assertColumnNotNull(conn, "PRODUCTS", "STORE_ID");
            assertColumnNotNull(conn, "CUSTOMERS", "STORE_ID");
            assertColumnNotNull(conn, "SUPPLIERS", "STORE_ID");

            // Todo registro pre-existente (incluido lo que el bug dejaba en NULL desde V3) quedo
            // asignado al local por defecto: no deberia haber huerfanos.
            assertEquals(0L, longValue(conn, "SELECT COUNT(*) FROM products WHERE store_id IS NULL"));
            assertEquals(0L, longValue(conn, "SELECT COUNT(*) FROM customers WHERE store_id IS NULL"));
            assertEquals(0L, longValue(conn, "SELECT COUNT(*) FROM suppliers WHERE store_id IS NULL"));

            // La unicidad de codigo interno / codigo de barras / identificacion / NIT ahora es
            // por local (store_id, codigo) y no global.
            assertUniqueConstraintColumns(conn, "UK_PRODUCTS_STORE_INTERNAL_CODE", Set.of("STORE_ID", "INTERNAL_CODE"));
            assertUniqueConstraintColumns(conn, "UK_PRODUCTS_STORE_BARCODE", Set.of("STORE_ID", "BARCODE"));
            assertUniqueConstraintColumns(conn, "UK_CUSTOMERS_STORE_IDENTIFICATION", Set.of("STORE_ID", "IDENTIFICATION"));
            assertUniqueConstraintColumns(conn, "UK_SUPPLIERS_STORE_TAX_ID", Set.of("STORE_ID", "TAX_ID"));
            assertFalse(constraintExists(conn, "UK_PRODUCTS_INTERNAL_CODE"), "la unicidad global de internal_code debio eliminarse");
            assertFalse(constraintExists(conn, "UK_PRODUCTS_BARCODE"), "la unicidad global de barcode debio eliminarse");
            assertFalse(constraintExists(conn, "UK_CUSTOMERS_IDENTIFICATION"), "la unicidad global de identification debio eliminarse");
            assertFalse(constraintExists(conn, "UK_SUPPLIERS_TAX_ID"), "la unicidad global de tax_id debio eliminarse");

            // settings: singleton eliminado. PK ahora es BIGINT (id del local), sin columna store_id.
            // (La fila en si la siembra DatabaseSeeder en el arranque real, no esta migracion.)
            assertFalse(columnExists(conn, "SETTINGS", "STORE_ID"), "store_id debe haberse eliminado de settings");
            assertEquals("BIGINT", columnType(conn, "SETTINGS", "ID"));
        }
    }

    private long longValue(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    private void assertColumnNotNull(Connection conn, String table, String column) throws SQLException {
        String sql = "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "Columna no encontrada: " + table + "." + column);
            assertEquals("NO", rs.getString(1), table + "." + column + " deberia ser NOT NULL");
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'";
        return longValue(conn, sql) > 0;
    }

    private String columnType(Connection conn, String table, String column) throws SQLException {
        String sql = "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    private boolean constraintExists(Connection conn, String constraintName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_NAME = '" + constraintName + "'";
        return longValue(conn, sql) > 0;
    }

    private void assertUniqueConstraintColumns(Connection conn, String constraintName, Set<String> expectedColumns) throws SQLException {
        Set<String> actual = new HashSet<>();
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE CONSTRAINT_NAME = '" + constraintName + "'";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                actual.add(rs.getString(1));
            }
        }
        assertEquals(expectedColumns, actual, "Columnas inesperadas para la restriccion " + constraintName);
    }
}
