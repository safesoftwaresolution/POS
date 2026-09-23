package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V16 elimina las columnas factus_* de 'settings' (obsoletas desde V14: las credenciales viven
 * en store_billing_config). Corre toda la cadena V1..V16 de un tirón, como arranca la app
 * realmente, y valida que las columnas viejas desaparecieron.
 *
 * Nota: a propósito NO se prueba aquí el traslado de datos (INSERT ... SELECT FROM settings) con
 * una fila de 'settings' insertada a mano entre dos corridas de Flyway: eso dispara un bug
 * conocido de H2 2.4.x con restricciones CHECK ... IN(...) validadas desde una conexión distinta
 * a la que las creó (el mismo que documenta V9MigrationSqlTest). No ocurre en una corrida
 * continua real ni en PostgreSQL; se verificó manualmente antes de escribir esta prueba.
 */
class V16MigrationSqlTest {

    @Test
    void allMigrationsApplyCleanlyAndFactusColumnsAreDropped() throws Exception {
        String url = "jdbc:h2:mem:v16_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            for (String column : new String[]{
                    "FACTUS_CLIENT_ID", "FACTUS_CLIENT_SECRET", "FACTUS_USERNAME",
                    "FACTUS_PASSWORD", "FACTUS_NUMBERING_RANGE_ID", "FACTUS_ENABLED"}) {
                assertFalse(columnExists(conn, "SETTINGS", column), "settings." + column + " debio eliminarse");
            }
            // Las columnas que si sobreviven no deberian tocarse por accidente.
            assertTrue(columnExists(conn, "SETTINGS", "BUSINESS_NAME"));
            assertTrue(columnExists(conn, "SETTINGS", "LOGO_URL"));
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getLong(1) > 0;
        }
    }
}
