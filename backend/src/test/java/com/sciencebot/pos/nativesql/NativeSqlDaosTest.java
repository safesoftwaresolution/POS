package com.sciencebot.pos.nativesql;

import com.sciencebot.pos.backoffice.BackofficeUserDto;
import com.sciencebot.pos.backoffice.StorePlanUsageDto;
import com.sciencebot.pos.backoffice.StoreWithoutOwnerDto;
import com.sciencebot.pos.backoffice.internal.repositories.UserDirectoryDao;
import com.sciencebot.pos.stores.StoreStatusAuditDto;
import com.sciencebot.pos.stores.internal.repositories.StorePurgeDao;
import com.sciencebot.pos.stores.internal.repositories.StoreStatusAuditQueryDao;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ejecuta el SQL nativo de los DAO contra el esquema real (cadena Flyway completa sobre H2), porque ningún
 * otro test lo cubre: un nombre de columna mal escrito o un orden de borrado que viole una FK solo se vería
 * en producción.
 */
class NativeSqlDaosTest {

    private static JdbcClient jdbc;

    @BeforeAll
    static void migrate() {
        String url = "jdbc:h2:mem:native_sql_daos_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(new DriverManagerDataSource(url, "sa", ""));

        // Solo para este test: H2 2.4.x falla al validar un CHECK ... IN (...) desde una conexión distinta
        // a la que lo creó (ver CLAUDE.md), y aquí cada sentencia abre la suya. Los CHECK no son lo que se prueba.
        for (String check : List.of("stores.CK_stores_status", "store_documents.CK_store_docs_status",
                "store_documents.CK_store_docs_type")) {
            String[] parts = check.split("\\.");
            jdbc.sql("ALTER TABLE " + parts[0] + " DROP CONSTRAINT IF EXISTS " + parts[1]).update();
        }
    }

    private static long store(String name, String email, String status) {
        jdbc.sql("INSERT INTO stores (name, email, tax_id, status, email_verified, created_at, updated_at) "
                + "VALUES (:n, :e, '900', :s, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("n", name).param("e", email).param("s", status).update();
        return jdbc.sql("SELECT id FROM stores WHERE email = :e").param("e", email).query(Long.class).single();
    }

    private static long user(String username, String role, Long storeId, boolean active, boolean verified) {
        jdbc.sql("INSERT INTO users (username, password, email, full_name, role, active, email_verified, store_id, "
                + "created_at, updated_at) VALUES (:u, 'x', :em, :fn, :r, :a, :v, :sid, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("u", username).param("em", username + "@test.com").param("fn", "Nombre " + username)
                .param("r", role).param("a", active).param("v", verified).param("sid", storeId).update();
        return jdbc.sql("SELECT id FROM users WHERE username = :u").param("u", username).query(Long.class).single();
    }

    @Test
    void userDirectory_filtersByRoleStoreAndUnassigned_andFindsStoresWithoutOwner() {
        long storeA = store("Dir Store A", "dir-a@test.com", "ACTIVE");
        store("Dir Store B", "dir-b@test.com", "ACTIVE");
        store("Dir Store C", "dir-c@test.com", "REJECTED");
        user("dir_adminA", "ADMINISTRATOR", storeA, true, true);
        user("dir_sellerA", "SELLER", storeA, true, true);
        user("dir_free", "ADMINISTRATOR", null, true, true);
        user("dir_unverified", "ADMINISTRATOR", null, true, false);

        UserDirectoryDao dao = new UserDirectoryDao(jdbc);

        Page<BackofficeUserDto> all = dao.search(null, null, "dir_", false, PageRequest.of(0, 50));
        assertEquals(4, all.getTotalElements());

        Page<BackofficeUserDto> firstPage = dao.search(null, null, "dir_", false, PageRequest.of(0, 2));
        assertEquals(2, firstPage.getContent().size());
        assertEquals(4, firstPage.getTotalElements());

        List<BackofficeUserDto> sellers = dao.search("SELLER", null, "dir_", false, PageRequest.of(0, 50)).getContent();
        assertEquals(1, sellers.size());
        assertEquals("Dir Store A", sellers.get(0).storeName());
        assertEquals("ACTIVE", sellers.get(0).storeStatus());
        assertEquals(storeA, sellers.get(0).storeId());

        assertEquals(2, dao.search(null, storeA, null, false, PageRequest.of(0, 50)).getTotalElements());

        List<BackofficeUserDto> unassigned = dao.search(null, null, "dir_", true, PageRequest.of(0, 50)).getContent();
        assertEquals(2, unassigned.size());
        assertTrue(unassigned.stream().allMatch(u -> u.storeId() == null && u.storeName() == null));

        // Buscar por nombre del local también encuentra a sus usuarios.
        assertEquals(2, dao.search(null, null, "dir store a", false, PageRequest.of(0, 50)).getTotalElements());

        List<String> assignable = dao.findAssignableOwners().stream().map(BackofficeUserDto::username).toList();
        assertTrue(assignable.contains("dir_free"));
        assertFalse(assignable.contains("dir_unverified"), "sin correo verificado no es asignable");
        assertFalse(assignable.contains("dir_adminA"), "ya tiene local");

        List<String> ownerless = dao.findStoresWithoutOwner().stream().map(StoreWithoutOwnerDto::name).toList();
        assertTrue(ownerless.contains("Dir Store B"));
        assertFalse(ownerless.contains("Dir Store A"), "tiene administrador activo");
        assertFalse(ownerless.contains("Dir Store C"), "los rechazados no cuentan");
    }

    @Test
    void storeWithOnlyAnInactiveAdministrator_countsAsWithoutOwner() {
        long id = store("Inactive Owner Store", "inactive-owner@test.com", "ACTIVE");
        user("inactive_owner", "ADMINISTRATOR", id, false, true);

        List<String> ownerless = new UserDirectoryDao(jdbc).findStoresWithoutOwner().stream()
                .map(StoreWithoutOwnerDto::name).toList();

        assertTrue(ownerless.contains("Inactive Owner Store"));
    }

    @Test
    void storeStatusAudit_readsTheSnapshotEvenWhenTheStoreNoLongerExists() {
        jdbc.sql("INSERT INTO store_status_audit (store_id, previous_status, new_status, reason, performed_by, created_at, "
                + "store_name, store_email, store_tax_id) VALUES (880001, 'REJECTED', 'DELETED', 'motivo x', 'root', "
                + "CURRENT_TIMESTAMP, 'Local Fantasma', 'fantasma@test.com', '777')").update();
        jdbc.sql("INSERT INTO store_status_audit (store_id, previous_status, new_status, performed_by, created_at, "
                + "store_name, store_email, store_tax_id) VALUES (880002, 'PENDING_VERIFICATION', 'ACTIVE', 'admin', "
                + "CURRENT_TIMESTAMP, 'Local Aprobado', 'aprobado@test.com', '888')").update();

        StoreStatusAuditQueryDao dao = new StoreStatusAuditQueryDao(jdbc);

        List<StoreStatusAuditDto> deleted = dao.search("DELETED", "fantasma", PageRequest.of(0, 10)).getContent();
        assertEquals(1, deleted.size());
        assertEquals("Local Fantasma", deleted.get(0).storeName());
        assertEquals("777", deleted.get(0).taxId());
        assertEquals("fantasma@test.com", deleted.get(0).storeEmail());
        assertEquals("REJECTED", deleted.get(0).previousStatus());

        assertEquals(1, dao.search(null, "root", PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, dao.search("ACTIVE", "fantasma", PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void storePurge_removesTheStoreAndItsDependenciesInForeignKeyOrder_leavingOthersUntouched() {
        long keep = store("Purge Keep", "purge-keep@test.com", "ACTIVE");
        long victim = store("Purge Victim", "purge-victim@test.com", "REJECTED");
        long keepUser = user("purge_keep_user", "ADMINISTRATOR", keep, true, true);
        long victimUser = user("purge_victim_user", "ADMINISTRATOR", victim, true, true);

        for (long uid : new long[]{keepUser, victimUser}) {
            jdbc.sql("INSERT INTO email_verification_tokens (token_hash, user_id, expires_at, created_at) "
                    + "VALUES (:h, :u, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                    .param("h", "evt-" + uid).param("u", uid).update();
            jdbc.sql("INSERT INTO password_reset_tokens (token_hash, user_id, expires_at, created_at) "
                    + "VALUES (:h, :u, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                    .param("h", "prt-" + uid).param("u", uid).update();
            jdbc.sql("INSERT INTO store_documents (store_id, document_type, document_url, status, uploaded_at) "
                    + "VALUES (:s, 'RUT', :url, 'PENDING', CURRENT_TIMESTAMP)")
                    .param("s", uid == keepUser ? keep : victim)
                    .param("url", "https://cdn.test/documentos/" + uid + ".pdf").update();
        }

        StorePurgeDao dao = new StorePurgeDao(jdbc);
        assertTrue(dao.findBusinessData(victim).isEmpty(), "una tienda sin operaciones no tiene datos de negocio");
        assertEquals(List.of("https://cdn.test/documentos/" + victimUser + ".pdf"), dao.findDocumentUrls(victim));

        dao.purge(victim);

        assertEquals(0L, count("SELECT COUNT(*) FROM stores WHERE id = " + victim));
        assertEquals(0L, count("SELECT COUNT(*) FROM users WHERE store_id = " + victim));
        assertEquals(0L, count("SELECT COUNT(*) FROM email_verification_tokens WHERE user_id = " + victimUser));
        assertEquals(0L, count("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = " + victimUser));
        assertEquals(0L, count("SELECT COUNT(*) FROM store_documents WHERE store_id = " + victim));

        assertEquals(1L, count("SELECT COUNT(*) FROM stores WHERE id = " + keep));
        assertEquals(1L, count("SELECT COUNT(*) FROM users WHERE id = " + keepUser));
        assertEquals(1L, count("SELECT COUNT(*) FROM email_verification_tokens WHERE user_id = " + keepUser));
        assertEquals(1L, count("SELECT COUNT(*) FROM store_documents WHERE store_id = " + keep));
    }

    @Test
    void storePlanUsage_countsActiveUsersAgainstThePlanCap() {
        long id = store("Plan Usage Store", "plan-usage@test.com", "ACTIVE");
        jdbc.sql("UPDATE stores SET plan_code = 'BASIC' WHERE id = :id").param("id", id).update();
        user("plan_admin", "ADMINISTRATOR", id, true, true);
        user("plan_seller", "SELLER", id, true, true);
        user("plan_old", "SELLER", id, false, true); // los inactivos no ocupan cupo

        List<StorePlanUsageDto> usage = new UserDirectoryDao(jdbc).findStorePlanUsage();
        StorePlanUsageDto mine = usage.stream().filter(u -> u.storeId() == id).findFirst().orElseThrow();

        assertEquals("BASIC", mine.planCode());
        assertEquals("Básico", mine.planName());
        assertEquals(2, mine.maxActiveUsers());
        assertEquals(2, mine.activeUsers());
    }

    private static long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
