package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Corre la cadena Flyway (V1..V25). V25 elimina por su nombre real (el que genera PostgreSQL) los UNIQUE
 * globales de V1; aquí solo se comprueba que la cadena aplica y que la unicidad por local sigue en pie.
 * El comportamiento sobre PostgreSQL (nombres auto-generados) no se puede reproducir en H2.
 */
class V25MigrationSqlTest {

    @Test
    void chainAppliesAndPerStoreUniquenessRemains() throws Exception {
        String url = "jdbc:h2:mem:v25_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

        try (Connection conn = DriverManager.getConnection(url, "sa", ""); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                     + "WHERE CONSTRAINT_NAME IN ('UK_CUSTOMERS_STORE_IDENTIFICATION', 'UK_PRODUCTS_STORE_INTERNAL_CODE', "
                     + "'UK_PRODUCTS_STORE_BARCODE', 'UK_SUPPLIERS_STORE_TAX_ID')")) {
            assertTrue(rs.next());
            assertEquals(4L, rs.getLong(1));
        }
    }
}
