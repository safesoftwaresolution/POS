package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V17 suma 'SET_DEFAULT' a las acciones permitidas en numbering_range_audit. Corre la cadena
 * completa V1..V17 de un tirón y revisa la definición del CHECK en INFORMATION_SCHEMA.
 *
 * Nota: a propósito NO se inserta una fila para probar el CHECK: validarlo desde una conexión
 * distinta a la de Flyway dispara el bug conocido de H2 2.4.x con CHECK ... IN(...) (ver
 * V16MigrationSqlTest). No ocurre en PostgreSQL.
 */
class V17MigrationSqlTest {

    @Test
    void setDefaultIsAddedToTheAllowedAuditActions() throws Exception {
        String url = "jdbc:h2:mem:v17_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        String sql = "SELECT CHECK_CLAUSE FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS "
                + "WHERE CONSTRAINT_NAME = 'CK_NUMBERING_RANGE_AUDIT_ACTION'";
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "el CHECK de acciones debe seguir existiendo");
            String clause = rs.getString(1);
            for (String action : new String[]{
                    "CREATE", "DELETE", "ACTIVATE", "DEACTIVATE", "UPDATE_CURRENT", "SET_DEFAULT"}) {
                assertTrue(clause.contains("'" + action + "'"), "falta la acción " + action + " en: " + clause);
            }
            assertFalse(rs.next(), "no debe quedar un CHECK duplicado");
        }
    }
}
