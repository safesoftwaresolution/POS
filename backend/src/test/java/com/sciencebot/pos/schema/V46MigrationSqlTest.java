package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V46 lets a refresh token remember the branch of its session; existing sessions keep using the user's store. */
class V46MigrationSqlTest {

    private static final String URL = "jdbc:h2:mem:v46_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").target(target).load();
    }

    @Test
    void existingRefreshTokensHaveNoBranch_andNewOnesCanPinOne() throws Exception {
        flyway("45").migrate();
        try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO users (full_name, username, email, password, role, active, email_verified, created_at, updated_at) "
                    + "VALUES ('Dueño', 'dueno', 'dueno@x.co', 'x', 'ADMINISTRATOR', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            st.executeUpdate("INSERT INTO refresh_tokens (token_hash, user_id, family_id, expires_at, revoked, created_at, updated_at) "
                    + "SELECT 'h1', id, 'fam-1', CURRENT_TIMESTAMP, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM users WHERE username = 'dueno'");
        }

        flyway("46").migrate();

        try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT store_id FROM refresh_tokens WHERE token_hash = 'h1'")) {
                assertTrue(rs.next());
                rs.getLong(1);
                assertTrue(rs.wasNull());
            }
            st.executeUpdate("INSERT INTO refresh_tokens (token_hash, user_id, family_id, store_id, expires_at, revoked, created_at, updated_at) "
                    + "SELECT 'h2', id, 'fam-1', 42, CURRENT_TIMESTAMP, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM users WHERE username = 'dueno'");
            try (ResultSet rs = st.executeQuery("SELECT store_id FROM refresh_tokens WHERE token_hash = 'h2'")) {
                rs.next();
                assertEquals(42L, rs.getLong(1));
            }
        }
    }
}
