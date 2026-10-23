package com.sciencebot.pos.stores.internal.repositories;

import com.sciencebot.pos.stores.StoreStatusAuditDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lectura del historial de estados de locales con SQL nativo (mismo patrón CQRS que reports):
 * une el registro de auditoría con los datos básicos del local sin cargar entidades.
 */
@Repository
public class StoreStatusAuditQueryDao {

    private final JdbcClient jdbcClient;

    public StoreStatusAuditQueryDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Page<StoreStatusAuditDto> search(String newStatus, String q, Pageable pageable) {
        List<String> filters = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (newStatus != null) {
            filters.add("a.new_status = :newStatus");
            params.put("newStatus", newStatus);
        }
        if (q != null) {
            filters.add("(LOWER(s.name) LIKE :q OR LOWER(s.email) LIKE :q OR LOWER(s.tax_id) LIKE :q"
                    + " OR LOWER(a.performed_by) LIKE :q)");
            params.put("q", "%" + q.toLowerCase() + "%");
        }
        String where = filters.isEmpty() ? "" : " WHERE " + String.join(" AND ", filters);
        String from = " FROM store_status_audit a JOIN stores s ON s.id = a.store_id" + where;

        long total = jdbcClient.sql("SELECT COUNT(*)" + from).params(params).query(Long.class).single();

        List<StoreStatusAuditDto> rows = jdbcClient.sql("""
                SELECT a.id, a.store_id, s.name AS store_name, s.tax_id, s.email AS store_email,
                       a.previous_status, a.new_status, a.reason, a.performed_by, a.created_at
                """ + from + " ORDER BY a.created_at DESC, a.id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", pageable.getPageSize())
                .param("offset", pageable.getOffset())
                .query((rs, i) -> new StoreStatusAuditDto(
                        rs.getLong("id"),
                        rs.getLong("store_id"),
                        rs.getString("store_name"),
                        rs.getString("tax_id"),
                        rs.getString("store_email"),
                        rs.getString("previous_status"),
                        rs.getString("new_status"),
                        rs.getString("reason"),
                        rs.getString("performed_by"),
                        rs.getTimestamp("created_at").toLocalDateTime()))
                .list();

        return new PageImpl<>(rows, pageable, total);
    }
}
