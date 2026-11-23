package com.sciencebot.pos.finance.internal.repositories;

import com.sciencebot.pos.finance.CategoryTotalDto;
import com.sciencebot.pos.finance.MovementType;
import com.sciencebot.pos.finance.PaymentMethodIncomeDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lecturas de solo lectura del estado financiero (base caja), por SQL como el módulo {@code reports}: no importa
 * las Facades de ventas, compras ni fiado. Todo filtra por el local. Los rangos de fecha-hora son
 * {@code [from, toExclusive)}; los de día ({@code expenses}), {@code [fromDay, toDay]} ambos incluidos.
 *
 * <p>Ingresos = ventas que no son a fiado + abonos de fiado + ingresos manuales − devoluciones de ventas que no
 * eran a fiado (una venta a fiado no entra dinero hasta que se abona; su devolución baja la deuda, no devuelve
 * efectivo). Los egresos manuales (`finance_movements` tipo EXPENSE) salen en {@link #sumMovements}.
 */
@Repository
public class FinanceReadDao {

    private final JdbcClient jdbcClient;

    public FinanceReadDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final String INCOME_ROWS = """
            SELECT s.payment_method AS paymentMethod, CAST(s.created_at AS DATE) AS flowDate, s.total_amount AS amount
            FROM sales s
            WHERE s.store_id = :storeId AND s.payment_method <> 'CREDIT'
                AND s.created_at >= :from AND s.created_at < :to
            UNION ALL
            SELECT COALESCE(m.payment_method, 'CASH'), CAST(m.created_at AS DATE), m.amount
            FROM credit_movements m
            WHERE m.store_id = :storeId AND m.type = 'PAYMENT'
                AND m.created_at >= :from AND m.created_at < :to
            UNION ALL
            SELECT s.payment_method, CAST(r.created_at AS DATE), -r.total_refund
            FROM sale_returns r
            JOIN sales s ON s.id = r.sale_id
            WHERE r.store_id = :storeId AND s.payment_method <> 'CREDIT'
                AND r.created_at >= :from AND r.created_at < :to
            UNION ALL
            SELECT m.payment_method, m.movement_date, m.amount
            FROM finance_movements m
            WHERE m.store_id = :storeId AND m.type = 'INCOME'
                AND m.movement_date >= CAST(:from AS DATE) AND m.movement_date < CAST(:to AS DATE)
            """;

    /** Ingresos netos por método de pago; su suma es el ingreso total. */
    public List<PaymentMethodIncomeDto> findIncomeByPaymentMethod(Long storeId, LocalDateTime from, LocalDateTime toExclusive) {
        String sql = "SELECT t.paymentMethod AS paymentMethod, SUM(t.amount) AS amountTotal FROM ("
                + INCOME_ROWS
                + ") t GROUP BY t.paymentMethod ORDER BY amountTotal DESC";
        return jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("from", from)
                .param("to", toExclusive)
                .query((rs, n) -> new PaymentMethodIncomeDto(rs.getString("paymentMethod"), rs.getBigDecimal("amountTotal")))
                .list();
    }

    /** Ingreso neto por día (solo los días con movimiento). */
    public Map<LocalDate, BigDecimal> findDailyIncome(Long storeId, LocalDateTime from, LocalDateTime toExclusive) {
        String sql = "SELECT t.flowDate AS flowDate, SUM(t.amount) AS amountTotal FROM ("
                + INCOME_ROWS
                + ") t GROUP BY t.flowDate ORDER BY t.flowDate";
        return toDayMap(jdbcClient.sql(sql)
                .param("storeId", storeId)
                .param("from", from)
                .param("to", toExclusive));
    }

    public BigDecimal sumPurchases(Long storeId, LocalDateTime from, LocalDateTime toExclusive) {
        return jdbcClient.sql("""
                SELECT COALESCE(SUM(p.total_amount), 0)
                FROM purchases p
                WHERE p.store_id = :storeId AND p.created_at >= :from AND p.created_at < :to
                """)
                .param("storeId", storeId)
                .param("from", from)
                .param("to", toExclusive)
                .query(BigDecimal.class)
                .single();
    }

    /** Suma de los movimientos manuales de un tipo ({@code EXPENSE} o {@code INCOME}) entre dos días, ambos incluidos. */
    public BigDecimal sumMovements(Long storeId, MovementType type, LocalDate fromDay, LocalDate toDay) {
        return jdbcClient.sql("""
                SELECT COALESCE(SUM(m.amount), 0)
                FROM finance_movements m
                WHERE m.store_id = :storeId AND m.type = :type
                    AND m.movement_date >= :fromDay AND m.movement_date <= :toDay
                """)
                .param("storeId", storeId)
                .param("type", type.name())
                .param("fromDay", fromDay)
                .param("toDay", toDay)
                .query(BigDecimal.class)
                .single();
    }

    public List<CategoryTotalDto> findExpensesByCategory(Long storeId, LocalDate fromDay, LocalDate toDay) {
        return jdbcClient.sql("""
                SELECT m.category AS category, SUM(m.amount) AS amountTotal
                FROM finance_movements m
                WHERE m.store_id = :storeId AND m.type = 'EXPENSE'
                    AND m.movement_date >= :fromDay AND m.movement_date <= :toDay
                GROUP BY m.category
                ORDER BY amountTotal DESC
                """)
                .param("storeId", storeId)
                .param("fromDay", fromDay)
                .param("toDay", toDay)
                .query((rs, n) -> new CategoryTotalDto(rs.getString("category"), rs.getBigDecimal("amountTotal")))
                .list();
    }

    /** Egreso por día: compras + gastos (los costos fijos no tienen fecha; se prorratean aparte). */
    public Map<LocalDate, BigDecimal> findDailyOutflow(Long storeId, LocalDate fromDay, LocalDate toDay) {
        return toDayMap(jdbcClient.sql("""
                SELECT t.flowDate AS flowDate, SUM(t.amount) AS amountTotal FROM (
                    SELECT CAST(p.created_at AS DATE) AS flowDate, p.total_amount AS amount
                    FROM purchases p
                    WHERE p.store_id = :storeId AND p.created_at >= :fromTs AND p.created_at < :toTs
                    UNION ALL
                    SELECT m.movement_date, m.amount
                    FROM finance_movements m
                    WHERE m.store_id = :storeId AND m.type = 'EXPENSE'
                        AND m.movement_date >= :fromDay AND m.movement_date <= :toDay
                ) t GROUP BY t.flowDate ORDER BY t.flowDate
                """)
                .param("storeId", storeId)
                .param("fromTs", fromDay.atStartOfDay())
                .param("toTs", toDay.plusDays(1).atStartOfDay())
                .param("fromDay", fromDay)
                .param("toDay", toDay));
    }

    /** Suma mensual de los costos fijos activos (sin prorratear). */
    public BigDecimal sumActiveFixedCostsMonthly(Long storeId) {
        return jdbcClient.sql("""
                SELECT COALESCE(SUM(c.monthly_amount), 0)
                FROM fixed_costs c
                WHERE c.store_id = :storeId AND c.active = TRUE
                """)
                .param("storeId", storeId)
                .query(BigDecimal.class)
                .single();
    }

    private static Map<LocalDate, BigDecimal> toDayMap(JdbcClient.StatementSpec spec) {
        Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
        spec.query((rs, n) -> {
            byDay.put(rs.getDate("flowDate").toLocalDate(), rs.getBigDecimal("amountTotal"));
            return null;
        }).list();
        return byDay;
    }
}
