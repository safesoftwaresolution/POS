package com.sciencebot.pos.stores;

import java.time.LocalDateTime;
import java.util.List;

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
    LocalDateTime createdAt,
    String planCode,
    String template,
    String theme,
    List<String> templates,
    List<String> extraTemplates
) {}