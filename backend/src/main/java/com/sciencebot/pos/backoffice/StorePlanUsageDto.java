package com.sciencebot.pos.backoffice;

/** Plan de un local y cuántos usuarios activos usa frente a su tope (administrador incluido). */
public record StorePlanUsageDto(
    Long storeId,
    String planCode,
    String planName,
    int maxActiveUsers,
    long activeUsers
) {}
