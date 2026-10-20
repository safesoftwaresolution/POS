package com.sciencebot.pos.settings;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Valida que la migración V6 se aplique limpiamente sobre H2 (modo PostgreSQL) y siembre
 * los catálogos base. Ejecuta el script SQL real directamente contra una H2 en memoria,
 * sin depender de la infraestructura Flyway del arranque (que en local no corre, porque el
 * esquema lo genera Hibernate vía ddl-auto). Así se prueba la sintaxis DDL + FK + índices +
 * inserts del seed de forma determinística. V6 no depende de V1..V5 (solo crea sus tablas).
 */
class V6MigrationSqlTest {

    private static final String MIGRATION = "/db/migration/V6__Dynamic_Parameter_Catalogs.sql";

    @Test
    void v6_appliesCleanlyOnH2AndSeedsCatalogs() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:v6_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")) {

            try (InputStream in = getClass().getResourceAsStream(MIGRATION)) {
                assertNotNull(in, "No se encontró el script de migración en el classpath: " + MIGRATION);
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    RunScript.execute(conn, reader); // falla si el DDL/seed no es válido en H2
                }
            }

            assertEquals(3, count(conn, "SELECT COUNT(*) FROM parameter_topics"));
            assertEquals(3, count(conn, "SELECT COUNT(*) FROM parameter_topics WHERE is_system = TRUE"));
            assertEquals(11, count(conn, "SELECT COUNT(*) FROM parameter_values"));
            assertEquals(4, count(conn,
                    "SELECT COUNT(*) FROM parameter_values v JOIN parameter_topics t ON v.topic_id = t.id " +
                    "WHERE t.code = 'PAYMENT_METHODS'"));
            // La FK y el seed enlazan correctamente el valor con su tema.
            assertEquals("Nequi / Daviplata", string(conn,
                    "SELECT v.label FROM parameter_values v JOIN parameter_topics t ON v.topic_id = t.id " +
                    "WHERE t.code = 'PAYMENT_METHODS' AND v.code = 'NEQUI'"));
        }
    }

    @Test
    void v6_enforcesUniqueValueCodePerTopic() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:v6_unique_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")) {

            try (InputStream in = getClass().getResourceAsStream(MIGRATION);
                 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                RunScript.execute(conn, reader);
            }

            // Duplicar (topic_id, code) debe violar UQ_param_value_topic_code.
            assertThrows(java.sql.SQLException.class, () -> {
                try (Statement s = conn.createStatement()) {
                    s.execute("INSERT INTO parameter_values (topic_id, code, label, sort_order, active, created_at, updated_at) " +
                            "VALUES ((SELECT id FROM parameter_topics WHERE code='PAYMENT_METHODS'), 'CASH', 'Dup', 9, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                }
            });
        }
    }

    private static long count(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String string(Connection conn, String sql) throws Exception {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
