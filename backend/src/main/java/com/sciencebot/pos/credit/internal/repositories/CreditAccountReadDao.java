package com.sciencebot.pos.credit.internal.repositories;

import com.sciencebot.pos.credit.CreditAccountDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lista de cuentas de fiado con el nombre del cliente (un JOIN de lectura, como los reportes: evita una consulta
 * por fila). Las condiciones se agregan solo si aplican para no depender de parámetros nulos sin tipo.
 */
@Repository
public class CreditAccountReadDao {

    private final JdbcClient jdbcClient;

    public CreditAccountReadDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * @param search          texto en minúsculas (o null) para nombre o identificación
     * @param onlyWithBalance solo cuentas con deuda
     * @param chargedByUserId si no es null, solo cuentas a las que ese usuario les fió (vista del vendedor)
     */
    public Page<CreditAccountDto> findAccounts(Long storeId, String search, boolean onlyWithBalance,
                                               Long chargedByUserId, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE a.store_id = :storeId");
        Map<String, Object> params = new HashMap<>();
        params.put("storeId", storeId);
        if (onlyWithBalance) {
            where.append(" AND a.balance > 0");
        }
        if (search != null) {
            where.append(" AND (LOWER(c.full_name) LIKE :search OR LOWER(c.identification) LIKE :search)");
            params.put("search", "%" + search + "%");
        }
        if (chargedByUserId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM credit_movements m2 WHERE m2.account_id = a.id "
                    + "AND m2.type = 'CHARGE' AND m2.user_id = :userId)");
            params.put("userId", chargedByUserId);
        }
        String from = " FROM credit_accounts a JOIN customers c ON c.id = a.customer_id" + where;

        Long total = jdbcClient.sql("SELECT COUNT(*)" + from).params(params).query(Long.class).single();

        List<CreditAccountDto> rows = new ArrayList<>(jdbcClient.sql(
                        "SELECT a.customer_id, c.full_name, c.identification, a.balance, a.credit_limit, "
                                + "(SELECT MAX(m.created_at) FROM credit_movements m WHERE m.account_id = a.id) AS last_movement"
                                + from + " ORDER BY a.balance DESC, c.full_name LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", pageable.getPageSize())
                .param("offset", pageable.getOffset())
                .query((rs, i) -> {
                    BigDecimal balance = rs.getBigDecimal("balance");
                    BigDecimal limit = rs.getBigDecimal("credit_limit");
                    Timestamp last = rs.getTimestamp("last_movement");
                    return new CreditAccountDto(
                            rs.getLong("customer_id"), rs.getString("full_name"), rs.getString("identification"),
                            balance, limit, limit == null ? null : limit.subtract(balance).max(BigDecimal.ZERO),
                            last == null ? null : last.toLocalDateTime());
                })
                .list());
        return new PageImpl<>(rows, pageable, total);
    }
}
