package com.sciencebot.pos.stores;

/** Estado del local del usuario autenticado (ACTIVE, PENDING_VERIFICATION, REJECTED, SUSPENDED o INACTIVE). */
public record StoreStatusDto(String status) {}
