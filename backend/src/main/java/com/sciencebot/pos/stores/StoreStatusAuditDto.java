package com.sciencebot.pos.stores;

import java.time.LocalDateTime;

public record StoreStatusAuditDto(
    Long id,
    Long storeId,
    String storeName,
    String taxId,
    String storeEmail,
    String previousStatus,
    String newStatus,
    String reason,
    String performedBy,
    LocalDateTime createdAt
) {}
