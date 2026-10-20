package com.sciencebot.pos.stores;

public record StoreMetricsDto(
    long totalStores,
    long activeStores,
    long inactiveStores,
    long pendingVerification,
    long suspended,
    long emailVerified
) {}