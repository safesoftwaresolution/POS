package com.sciencebot.pos.backoffice;

import java.time.LocalDateTime;

/** Usuario visto desde el backoffice: incluye el local al que pertenece y su estado. Solo lectura. */
public record BackofficeUserDto(
    Long id,
    String fullName,
    String username,
    String email,
    String role,
    boolean active,
    boolean emailVerified,
    Long storeId,
    String storeName,
    String storeStatus,
    LocalDateTime createdAt
) {}
