package com.sciencebot.pos.backoffice;

public record OnboardingStatsDto(
    long pendingUsers,
    long pendingStores,
    long approvedStoresLast30Days,
    long rejectedStoresTotal
) {}
