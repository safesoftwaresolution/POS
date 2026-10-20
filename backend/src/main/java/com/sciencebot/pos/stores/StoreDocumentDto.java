package com.sciencebot.pos.stores;

import java.time.LocalDateTime;

public record StoreDocumentDto(
    Long id,
    Long storeId,
    String documentType,
    String documentUrl,
    String status,
    String rejectionReason,
    LocalDateTime uploadedAt,
    LocalDateTime verifiedAt
) {}