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
 * V26 crea user_deletion_audit. Corre la cadena completa y revisa las columnas de las que depende
 * UserDeletionAudit, y que la tabla NO tenga llave foranea: el registro debe sobrevivir al usuario borrado.
 */
class V26MigrationSqlTest {

    @Test
    void userDeletionAuditTableIsCreatedWithoutForeignKeys() throws Exception {
        String url = "jdbc:h2:mem:v26_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement st = conn.createStatement()) {

            Map<String, String> nullable = new HashMap<>();
            try (ResultSet rs = st.executeQuery("SELECT COLUMN_NAME, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_NAME = 'USER_DELETION_AUDIT'")) {
                while (rs.next()) {
                    nullable.put(rs.getString(1), rs.getString(2));
                }
            }
            for (String column : new String[]{
                    "ID", "USER_ID", "USERNAME", "FULL_NAME", "EMAIL", "ROLE", "STORE_ID", "KIND", "PERFORMED_BY", "CREATED_AT"}) {
                assertTrue(nullable.containsKey(column), "falta la columna " + column);
            }
            assertEquals("NO", nullable.get("USER_ID"));
            assertEquals("NO", nullable.get("USERNAME"));
            assertEquals("NO", nullable.get("KIND"));
            assertEquals("YES", nullable.get("STORE_ID"), "un administrador pendiente no tiene local");

            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_NAME = 'USER_DELETION_AUDIT' AND CONSTRAINT_TYPE = 'FOREIGN KEY'")) {
                rs.next();
                assertEquals(0, rs.getInt(1), "el registro debe sobrevivir al usuario y al local borrados");
            }
        }
    }
}
