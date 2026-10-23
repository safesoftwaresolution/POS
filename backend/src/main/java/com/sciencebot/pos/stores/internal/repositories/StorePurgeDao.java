package com.sciencebot.pos.stores.internal.repositories;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Eliminación definitiva de un local y de todo lo que cuelga de él, con SQL nativo.
 *
 * <p>Excepción deliberada a "cada módulo toca solo sus tablas": un local es la raíz del tenant y su
 * borrado completo cruza usuarios, tokens, notificaciones, configuración, billing y soporte. Hacerlo
 * por Facades obligaría a añadir una operación de purga en cada módulo, y una sola transacción nativa
 * es más simple y atómica. Solo se usa desde {@code StoreServiceImpl.deleteRejectedStore}, que antes
 * comprueba el estado y que el local nunca haya operado ({@link #findBusinessData}).
 */
@Repository
public class StorePurgeDao {

    /** Tablas de negocio con store_id: si alguna tiene filas, el local operó y no se puede purgar. */
    private static final List<String> BUSINESS_TABLES = List.of(
            "products", "customers", "suppliers", "sales", "purchases",
            "inventory_movements", "sale_returns", "electronic_invoices");

    private final JdbcClient jdbcClient;

    public StorePurgeDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Nombres de las tablas de negocio que ya tienen datos de este local (vacío si nunca operó). */
    public List<String> findBusinessData(Long storeId) {
        List<String> withData = new ArrayList<>();
        for (String table : BUSINESS_TABLES) {
            long count = jdbcClient.sql("SELECT COUNT(*) FROM " + table + " WHERE store_id = :storeId")
                    .param("storeId", storeId).query(Long.class).single();
            if (count > 0) {
                withData.add(table);
            }
        }
        return withData;
    }

    /** Claves/URLs de los documentos KYC del local, para borrar también los archivos del bucket. */
    public List<String> findDocumentUrls(Long storeId) {
        return jdbcClient.sql("SELECT document_url FROM store_documents WHERE store_id = :storeId")
                .param("storeId", storeId).query(String.class).list();
    }

    /** Borra el local y sus dependencias, en orden de FKs. Debe ejecutarse dentro de una transacción. */
    public void purge(Long storeId) {
        String usersOfStore = "(SELECT id FROM users WHERE store_id = :storeId)";
        delete("DELETE FROM refresh_tokens WHERE user_id IN " + usersOfStore, storeId);
        delete("DELETE FROM email_verification_tokens WHERE user_id IN " + usersOfStore, storeId);
        delete("DELETE FROM password_reset_tokens WHERE user_id IN " + usersOfStore, storeId);
        delete("DELETE FROM users WHERE store_id = :storeId", storeId);
        delete("DELETE FROM notifications WHERE store_id = :storeId", storeId);
        delete("DELETE FROM store_documents WHERE store_id = :storeId", storeId);
        delete("DELETE FROM support_tickets WHERE store_id = :storeId", storeId);
        delete("DELETE FROM product_subcategories WHERE store_id = :storeId", storeId);
        delete("DELETE FROM numbering_range_audit WHERE store_id = :storeId", storeId);
        delete("DELETE FROM store_billing_config WHERE store_id = :storeId", storeId);
        delete("DELETE FROM settings WHERE id = :storeId", storeId);
        delete("DELETE FROM stores WHERE id = :storeId", storeId);
    }

    private void delete(String sql, Long storeId) {
        jdbcClient.sql(sql).param("storeId", storeId).update();
    }
}
