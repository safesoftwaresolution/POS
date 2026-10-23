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
        long victimSeller = user("purge_victim_seller", "SELLER", victim, true, true);

        for (long uid : new long[]{keepUser, victimUser, victimSeller}) {
            jdbc.sql("INSERT INTO email_verification_tokens (token_hash, user_id, expires_at, created_at) "
                    + "VALUES (:h, :u, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                    .param("h", "evt-" + uid).param("u", uid).update();
            jdbc.sql("INSERT INTO password_reset_tokens (token_hash, user_id, expires_at, created_at) "
                    + "VALUES (:h, :u, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                    .param("h", "prt-" + uid).param("u", uid).update();
        }
        for (long[] pair : new long[][]{{keepUser, keep}, {victimUser, victim}}) {
            jdbc.sql("INSERT INTO store_documents (store_id, document_type, document_url, status, uploaded_at) "
                    + "VALUES (:s, 'RUT', :url, 'PENDING', CURRENT_TIMESTAMP)")
                    .param("s", pair[1]).param("url", "https://cdn.test/documentos/" + pair[0] + ".pdf").update();
        }

        StorePurgeDao dao = new StorePurgeDao(jdbc);
        assertTrue(dao.findBusinessData(victim).isEmpty(), "una tienda sin operaciones no tiene datos de negocio");
        assertEquals(0L, dao.countValidatedInvoices(victim));
        assertEquals(List.of("https://cdn.test/documentos/" + victimUser + ".pdf"), dao.findDocumentUrls(victim));

        dao.purge(victim);

        assertEquals(0L, count("SELECT COUNT(*) FROM stores WHERE id = " + victim));
        assertEquals(0L, count("SELECT COUNT(*) FROM users WHERE store_id = " + victim));
        assertEquals(0L, count("SELECT COUNT(*) FROM users WHERE id = " + victimSeller), "el personal se elimina");
        assertEquals(0L, count("SELECT COUNT(*) FROM email_verification_tokens WHERE user_id = " + victimSeller));
        assertEquals(0L, count("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = " + victimUser));
        assertEquals(0L, count("SELECT COUNT(*) FROM store_documents WHERE store_id = " + victim));

        // El administrador del local no se elimina: queda sin local y sigue siendo un candidato a asignar.
        assertEquals(1L, count("SELECT COUNT(*) FROM users WHERE id = " + victimUser + " AND store_id IS NULL AND active = TRUE"));
        assertEquals(1L, count("SELECT COUNT(*) FROM email_verification_tokens WHERE user_id = " + victimUser));
        assertTrue(new UserDirectoryDao(jdbc).findAssignableOwners().stream()
                .anyMatch(u -> u.username().equals("purge_victim_user")));

        assertEquals(1L, count("SELECT COUNT(*) FROM stores WHERE id = " + keep));
        assertEquals(1L, count("SELECT COUNT(*) FROM users WHERE id = " + keepUser));
        assertEquals(1L, count("SELECT COUNT(*) FROM email_verification_tokens WHERE user_id = " + keepUser));
        assertEquals(1L, count("SELECT COUNT(*) FROM store_documents WHERE store_id = " + keep));
    }

    @Test
    void storePurge_alsoRemovesTheOperationOfAStoreThatSold_andCountsValidatedInvoices() {
        long keep = store("Op Keep", "op-keep@test.com", "ACTIVE");
        long victim = store("Op Victim", "op-victim@test.com", "ACTIVE");
        long keepAdmin = user("op_keep_admin", "ADMINISTRATOR", keep, true, true);
        long victimAdmin = user("op_victim_admin", "ADMINISTRATOR", victim, true, true);
        long victimSeller = user("op_victim_seller", "SELLER", victim, true, true);
        long keepSale = operate(keep, keepAdmin, "OP-KEEP");
        long victimSale = operate(victim, victimSeller, "OP-VICTIM");
        jdbc.sql("INSERT INTO electronic_invoices (sale_id, status, store_id, created_at, updated_at) "
                + "VALUES (:sale, 'VALIDATED', :store, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("sale", victimSale).param("store", victim).update();

        StorePurgeDao dao = new StorePurgeDao(jdbc);
        assertTrue(dao.findBusinessData(victim).containsAll(List.of("products", "customers", "sales")));
        assertEquals(1L, dao.countValidatedInvoices(victim));
        assertEquals(0L, dao.countValidatedInvoices(keep));

        dao.purge(victim);

        for (String table : List.of("products", "customers", "sales", "electronic_invoices")) {
            assertEquals(0L, count("SELECT COUNT(*) FROM " + table + " WHERE store_id = " + victim), table);
        }
        assertEquals(0L, count("SELECT COUNT(*) FROM sale_items WHERE sale_id = " + victimSale));
        assertEquals(0L, count("SELECT COUNT(*) FROM users WHERE id = " + victimSeller));
        assertEquals(1L, count("SELECT COUNT(*) FROM users WHERE id = " + victimAdmin + " AND store_id IS NULL"));

        assertEquals(1L, count("SELECT COUNT(*) FROM sales WHERE id = " + keepSale));
        assertEquals(1L, count("SELECT COUNT(*) FROM sale_items WHERE sale_id = " + keepSale));
        assertEquals(1L, count("SELECT COUNT(*) FROM products WHERE store_id = " + keep));
    }

    /** Un producto, un cliente y una venta con un renglón, más una compra y un movimiento de inventario. */
    private static long operate(long storeId, long userId, String tag) {
        jdbc.sql("INSERT INTO categories (name, created_at, updated_at) "
                + "SELECT :c, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = :c)")
                .param("c", "Cat Purge").update();
        jdbc.sql("INSERT INTO products (internal_code, name, category_id, purchase_price, sale_price, quantity_available, "
                + "store_id, created_at, updated_at) VALUES (:c, :c, (SELECT id FROM categories WHERE name = 'Cat Purge'), "
                + "5, 10, 5, :s, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("c", tag + "-P").param("s", storeId).update();
        long product = jdbc.sql("SELECT id FROM products WHERE internal_code = :c").param("c", tag + "-P").query(Long.class).single();
        jdbc.sql("INSERT INTO customers (full_name, identification, store_id, active, created_at, updated_at) "
                + "VALUES (:c, :c, :s, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                .param("c", tag + "-C").param("s", storeId).update();
        long customer = jdbc.sql("SELECT id FROM customers WHERE identification = :c").param("c", tag + "-C").query(Long.class).single();
        jdbc.sql("INSERT INTO sales (invoice_number, customer_id, total_amount, cash_received, cash_change, user_id, "
                + "store_id, created_at) VALUES (:n, :c, 10, 10, 0, :u, :s, CURRENT_TIMESTAMP)")
                .param("n", tag).param("c", customer).param("u", userId).param("s", storeId).update();
        long sale = jdbc.sql("SELECT id FROM sales WHERE invoice_number = :n").param("n", tag).query(Long.class).single();
        jdbc.sql("INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, subtotal) "
                + "VALUES (:sale, :p, 1, 10, 10)").param("sale", sale).param("p", product).update();
        return sale;
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
