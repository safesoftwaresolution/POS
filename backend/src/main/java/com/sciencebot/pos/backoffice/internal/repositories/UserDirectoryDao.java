package com.sciencebot.pos.backoffice.internal.repositories;

import com.sciencebot.pos.backoffice.BackofficeUserDto;
import com.sciencebot.pos.backoffice.StorePlanUsageDto;
import com.sciencebot.pos.backoffice.StoreWithoutOwnerDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lectura pura del directorio de usuarios para el backoffice (mismo patrón CQRS que reports y
 * {@link OnboardingStatsDao}): SQL nativo que une users con stores sin cargar entidades.
 */
@Repository
public class UserDirectoryDao {

    private static final String SELECT_USER = """
            SELECT u.id, u.full_name, u.username, u.email, u.role, u.active, u.email_verified, u.store_id,
                   s.name AS store_name, s.status AS store_status, u.created_at
            """;

    private final JdbcClient jdbcClient;

    public UserDirectoryDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * @param role       filtra por rol (nulo = todos)
     * @param storeId    solo los usuarios de ese local (nulo = todos)
     * @param q          texto libre sobre nombre, usuario, correo o nombre del local
     * @param unassigned true = solo ADMINISTRATOR sin local
     */
    public Page<BackofficeUserDto> search(String role, Long storeId, String q, boolean unassigned, Pageable pageable) {
        List<String> filters = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (role != null) {
            filters.add("u.role = :role");
            params.put("role", role);
        }
        if (storeId != null) {
            filters.add("u.store_id = :storeId");
            params.put("storeId", storeId);
        }
        if (unassigned) {
            filters.add("u.store_id IS NULL AND u.role = 'ADMINISTRATOR'");
        }
        if (q != null) {
            filters.add("(LOWER(u.full_name) LIKE :q OR LOWER(u.username) LIKE :q OR LOWER(u.email) LIKE :q"
                    + " OR LOWER(s.name) LIKE :q)");
            params.put("q", "%" + q.toLowerCase() + "%");
        }
        String where = filters.isEmpty() ? "" : " WHERE " + String.join(" AND ", filters);
        String from = " FROM users u LEFT JOIN stores s ON s.id = u.store_id" + where;

        long total = jdbcClient.sql("SELECT COUNT(*)" + from).params(params).query(Long.class).single();

        List<BackofficeUserDto> rows = jdbcClient
                .sql(SELECT_USER + from + " ORDER BY u.id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", pageable.getPageSize())
                .param("offset", pageable.getOffset())
                .query(UserDirectoryDao::mapUser)
                .list();
        return new PageImpl<>(rows, pageable, total);
    }

    /** ADMINISTRATOR activos con el correo verificado que aún no tienen local: candidatos a asignar. */
    public List<BackofficeUserDto> findAssignableOwners() {
        return jdbcClient.sql(SELECT_USER + """
                FROM users u LEFT JOIN stores s ON s.id = u.store_id
                WHERE u.role = 'ADMINISTRATOR' AND u.store_id IS NULL AND u.active = TRUE AND u.email_verified = TRUE
                ORDER BY u.id DESC
                """).query(UserDirectoryDao::mapUser).list();
    }

    /** Locales (no rechazados) sin ningún ADMINISTRATOR activo. */
    public List<StoreWithoutOwnerDto> findStoresWithoutOwner() {
        return jdbcClient.sql("""
                SELECT s.id, s.name, s.email, s.tax_id, s.status
                FROM stores s
                WHERE s.status <> 'REJECTED'
                  AND NOT EXISTS (SELECT 1 FROM users u
                                  WHERE u.store_id = s.id AND u.role = 'ADMINISTRATOR' AND u.active = TRUE)
                ORDER BY s.created_at DESC
                """).query((rs, i) -> new StoreWithoutOwnerDto(
                rs.getLong("id"), rs.getString("name"), rs.getString("email"),
                rs.getString("tax_id"), rs.getString("status"))).list();
    }

    /** Plan de cada local y usuarios activos que usa (el tope incluye al administrador). */
    public List<StorePlanUsageDto> findStorePlanUsage() {
        return jdbcClient.sql("""
                SELECT s.id, s.plan_code, p.name AS plan_name, p.max_additional_users,
                       (SELECT COUNT(*) FROM users u WHERE u.store_id = s.id AND u.active = TRUE) AS active_users
                FROM stores s JOIN plans p ON p.code = s.plan_code
                ORDER BY s.id
                """).query((rs, i) -> new StorePlanUsageDto(
                rs.getLong("id"), rs.getString("plan_code"), rs.getString("plan_name"),
                1 + rs.getInt("max_additional_users"), rs.getLong("active_users"))).list();
    }

    private static BackofficeUserDto mapUser(ResultSet rs, int rowNum) throws SQLException {
        long storeId = rs.getLong("store_id");
        Long storeIdOrNull = rs.wasNull() ? null : storeId;
        return new BackofficeUserDto(
                rs.getLong("id"),
                rs.getString("full_name"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getBoolean("active"),
                rs.getBoolean("email_verified"),
                storeIdOrNull,
                rs.getString("store_name"),
                rs.getString("store_status"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }
}
