package com.sciencebot.pos.stores;

import java.time.LocalDateTime;

public record StoreDto(
    Long id,
    String name,
    Long storeCategoryId,
    String categoryName,
    String phone,
    String email,
    String website,
    String address,
    String taxId,
    String status,
    boolean emailVerified,
    String rejectionReason,
    LocalDateTime createdAt
) {}