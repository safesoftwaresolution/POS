package com.sciencebot.pos.backoffice;

/**
 * Embudo de auto-registro. {@code emailProvider} es el modo de correo activo ({@code smtp} o {@code log}): con
 * {@code log} los correos de verificación y aprobación no salen, y el backoffice lo advierte.
 */
public record OnboardingStatsDto(
    long pendingUsers,
    long pendingStores,
    long approvedStoresLast30Days,
    long rejectedStoresTotal,
    String emailProvider
) {}
