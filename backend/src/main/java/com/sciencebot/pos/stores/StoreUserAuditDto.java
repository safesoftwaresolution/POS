package com.sciencebot.pos.stores;

import java.time.LocalDateTime;

public record StoreUserAuditDto(
    Long id,
    Long storeId,
    String storeName,
    Long userId,
    String username,
    String fullName,
    String role,
    String action,
    String performedBy,
    LocalDateTime createdAt
) {}
