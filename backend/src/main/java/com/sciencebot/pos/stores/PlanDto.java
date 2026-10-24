package com.sciencebot.pos.stores;

public record PlanDto(
    String code,
    String name,
    int maxAdditionalUsers,
    /** Total de usuarios activos permitidos: el administrador más los adicionales. */
    int maxActiveUsers
) {}
