package com.sciencebot.pos.users;

/** Uso de usuarios del local del llamador frente al tope de su plan. */
public record UserQuotaStatusDto(
    String planName,
    long activeUsers,
    int maxActiveUsers,
    boolean canCreateMore
) {}
