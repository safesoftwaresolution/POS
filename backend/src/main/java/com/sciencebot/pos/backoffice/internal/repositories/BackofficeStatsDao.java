package com.sciencebot.pos.backoffice.internal.repositories;

import com.sciencebot.pos.backoffice.BackofficeStatsDto;
import com.sciencebot.pos.backoffice.BackofficeStatsDto.CountItem;
import com.sciencebot.pos.backoffice.BackofficeStatsDto.Kpis;
import com.sciencebot.pos.backoffice.BackofficeStatsDto.MonthPoint;
import com.sciencebot.pos.backoffice.BackofficeStatsDto.TopStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lectura agregada para el panel de estadísticas del backoffice: bypassa JPA con SQL nativo
 * (SUM/COUNT/GROUP BY), igual que el módulo reports y {@link OnboardingStatsDao}.
 */
@Repository
public class BackofficeStatsDao {

    private static final int MONTHS = 12;
    private static final int TOP_STORES = 5;

    private final JdbcClient jdbcClient;

    public BackofficeStatsDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public BackofficeStatsDto overview() {
        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
        YearMonth firstMonth = YearMonth.now().minusMonths(MONTHS - 1);
        LocalDateTime seriesStart = firstMonth.atDay(1).atStartOfDay();

        return new BackofficeStatsDto(
                kpis(last30Days),
                countBy("SELECT status AS k, COUNT(*) AS c FROM stores GROUP BY status ORDER BY c DESC"),
                countBy("SELECT p.name AS k, COUNT(*) AS c FROM stores s JOIN plans p ON p.code = s.plan_code "
                        + "GROUP BY p.name, p.sort_order ORDER BY p.sort_order"),
                countBy("SELECT role AS k, COUNT(*) AS c FROM users WHERE active = TRUE GROUP BY role ORDER BY c DESC"),
                countBy("SELECT status AS k, COUNT(*) AS c FROM electronic_invoices GROUP BY status ORDER BY c DESC"),
                monthlySeries("SELECT EXTRACT(YEAR FROM created_at) AS y, EXTRACT(MONTH FROM created_at) AS m, "
                        + "COUNT(*) AS c, CAST(0 AS DECIMAL(14,2)) AS a FROM stores WHERE created_at >= :since "
                        + "GROUP BY EXTRACT(YEAR FROM created_at), EXTRACT(MONTH FROM created_at)", seriesStart, firstMonth),
                monthlySeries("SELECT EXTRACT(YEAR FROM created_at) AS y, EXTRACT(MONTH FROM created_at) AS m, "
                        + "COUNT(*) AS c, COALESCE(SUM(total_amount), 0) AS a FROM sales WHERE created_at >= :since "
                        + "GROUP BY EXTRACT(YEAR FROM created_at), EXTRACT(MONTH FROM created_at)", seriesStart, firstMonth),
                topStoresBySales(last30Days));
    }

    private Kpis kpis(LocalDateTime last30Days) {
        long totalStores = scalar("SELECT COUNT(*) FROM stores");
        long activeStores = scalar("SELECT COUNT(*) FROM stores WHERE status = 'ACTIVE'");
        long activeUsers = scalar("SELECT COUNT(*) FROM users WHERE active = TRUE");
        long newStores = jdbcClient.sql("SELECT COUNT(*) FROM stores WHERE created_at >= :since")
                .param("since", last30Days).query(Long.class).single();
        long sales = jdbcClient.sql("SELECT COUNT(*) FROM sales WHERE created_at >= :since")
                .param("since", last30Days).query(Long.class).single();
        BigDecimal salesAmount = jdbcClient.sql("SELECT COALESCE(SUM(total_amount), 0) FROM sales WHERE created_at >= :since")
                .param("since", last30Days).query(BigDecimal.class).single();
        long storesWithSales = jdbcClient.sql("SELECT COUNT(DISTINCT store_id) FROM sales WHERE created_at >= :since")
                .param("since", last30Days).query(Long.class).single();
        long atLimit = scalar("""
                SELECT COUNT(*) FROM stores s JOIN plans p ON p.code = s.plan_code
                WHERE (SELECT COUNT(*) FROM users u WHERE u.store_id = s.id AND u.active = TRUE)
                      >= 1 + p.max_additional_users
                """);
        return new Kpis(totalStores, activeStores, activeUsers, newStores, sales, salesAmount, storesWithSales, atLimit);
    }

    private List<TopStore> topStoresBySales(LocalDateTime since) {
        return jdbcClient.sql("""
                SELECT s.id, s.name, COUNT(sa.id) AS sales, COALESCE(SUM(sa.total_amount), 0) AS amount
                FROM sales sa JOIN stores s ON s.id = sa.store_id
                WHERE sa.created_at >= :since
                GROUP BY s.id, s.name
                ORDER BY amount DESC, sales DESC
                LIMIT :top
                """).param("since", since).param("top", TOP_STORES)
                .query((rs, i) -> new TopStore(rs.getLong("id"), rs.getString("name"),
                        rs.getLong("sales"), rs.getBigDecimal("amount"))).list();
    }

    private long scalar(String sql) {
        return jdbcClient.sql(sql).query(Long.class).single();
    }

    private List<CountItem> countBy(String sql) {
        return jdbcClient.sql(sql).query((rs, i) -> new CountItem(rs.getString("k"), rs.getLong("c"))).list();
    }

    /** Agrupa por mes y completa con ceros los meses sin datos, para que la serie no tenga huecos. */
    private List<MonthPoint> monthlySeries(String sql, LocalDateTime since, YearMonth firstMonth) {
        Map<YearMonth, MonthPoint> byMonth = new HashMap<>();
        jdbcClient.sql(sql).param("since", since).query((rs, i) -> {
            YearMonth ym = YearMonth.of(rs.getInt("y"), rs.getInt("m"));
            byMonth.put(ym, new MonthPoint(ym.toString(), rs.getLong("c"), rs.getBigDecimal("a")));
            return ym;
        }).list();

        List<MonthPoint> series = new ArrayList<>();
        for (int i = 0; i < MONTHS; i++) {
            YearMonth ym = firstMonth.plusMonths(i);
            series.add(byMonth.getOrDefault(ym, new MonthPoint(ym.toString(), 0, BigDecimal.ZERO)));
        }
        return series;
    }
}
