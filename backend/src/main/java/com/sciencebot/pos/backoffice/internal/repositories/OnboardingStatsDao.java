package com.sciencebot.pos.backoffice.internal.repositories;

import com.sciencebot.pos.backoffice.OnboardingStatsDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Lectura agregada pura para el panel de "Pendientes" del backoffice: bypassa JPA
 * con SQL nativo sobre users/stores, mismo patron CQRS usado por el modulo reports.
 */
@Repository
public class OnboardingStatsDao {

    private final JdbcClient jdbcClient;

    public OnboardingStatsDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public OnboardingStatsDto getStats() {
        long pendingUsers = jdbcClient.sql("""
                SELECT COUNT(*) FROM users WHERE role = 'ADMINISTRATOR' AND email_verified = false
                """).query(Long.class).single();

        long pendingStores = jdbcClient.sql("""
                SELECT COUNT(*) FROM stores WHERE status = 'PENDING_VERIFICATION'
                """).query(Long.class).single();

        long approvedLast30Days = jdbcClient.sql("""
                SELECT COUNT(*) FROM stores WHERE status = 'ACTIVE' AND updated_at >= :since
                """).param("since", LocalDateTime.now().minusDays(30)).query(Long.class).single();

        long rejectedTotal = jdbcClient.sql("""
                SELECT COUNT(*) FROM stores WHERE status = 'REJECTED'
                """).query(Long.class).single();

        return new OnboardingStatsDto(pendingUsers, pendingStores, approvedLast30Days, rejectedTotal);
    }
}
