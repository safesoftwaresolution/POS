package com.sciencebot.pos.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V45 adds businesses (a group of stores under one owner) without touching existing stores: they keep
 * business_id NULL. A store belongs to one business and a user owns at most one.
 */
class V45MigrationSqlTest {

    private static final String URL = "jdbc:h2:mem:v45_migration_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").target(target).load();
    }

    private static long insertStore(Statement st, String email) throws SQLException {
        // H2 2.4.x cannot validate CHECK ... IN (...) from a connection other than the one that created it.
        st.executeUpdate("ALTER TABLE stores DROP CONSTRAINT IF EXISTS CK_stores_status");
        st.executeUpdate("INSERT INTO stores (name, email, status, email_verified, plan_code, created_at, updated_at) "
                + "VALUES ('Local', '" + email + "', 'ACTIVE', TRUE, 'BASIC', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        try (ResultSet rs = st.executeQuery("SELECT id FROM stores WHERE email = '" + email + "'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long insertUser(Statement st, String username, long storeId) throws SQLException {
        st.executeUpdate("INSERT INTO users (full_name, username, email, password, role, active, store_id, email_verified, created_at, updated_at) "
                + "VALUES ('Dueño', '" + username + "', '" + username + "@x.co', 'x', 'ADMINISTRATOR', TRUE, " + storeId
                + ", TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        try (ResultSet rs = st.executeQuery("SELECT id FROM users WHERE username = '" + username + "'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void insertBusiness(Statement st, String name, long ownerId, long mainStoreId) throws SQLException {
        st.executeUpdate("INSERT INTO businesses (name, owner_user_id, main_store_id, billing_mode, catalog_mode, max_branches, "
                + "created_at, updated_at) VALUES ('" + name + "', " + ownerId + ", " + mainStoreId
                + ", 'SHARED_TAX_ID', 'INDEPENDENT', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }

    @Test
    void existingStoresStayStandalone_andABusinessCanGroupStores() throws Exception {
        flyway("44").migrate();
        long storeId;
        try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
            storeId = insertStore(st, "a@x.co");
        }

        flyway("45").migrate();

        try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT business_id FROM stores WHERE id = " + storeId)) {
                rs.next();
                rs.getLong(1);
                assertEquals(true, rs.wasNull());
            }

            long ownerId = insertUser(st, "dueno", storeId);
            insertBusiness(st, "Mi Negocio", ownerId, storeId);
            st.executeUpdate("UPDATE stores SET business_id = (SELECT id FROM businesses WHERE name = 'Mi Negocio') WHERE id = " + storeId);
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM stores WHERE business_id IS NOT NULL")) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    void aUserOwnsAtMostOneBusiness_andAStoreIsTheMainOfOnlyOne() throws Exception {
        flyway("45").migrate();
        try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
            long storeA = insertStore(st, "b@x.co");
            long storeB = insertStore(st, "c@x.co");
            long ownerId = insertUser(st, "dueno2", storeA);
            insertBusiness(st, "Uno", ownerId, storeA);

            assertThrows(SQLException.class, () -> insertBusiness(st, "Dos", ownerId, storeB));
            long otherOwner = insertUser(st, "otro", storeB);
            assertThrows(SQLException.class, () -> insertBusiness(st, "Tres", otherOwner, storeA));
        }
    }
}
