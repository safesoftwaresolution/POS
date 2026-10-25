package com.sciencebot.pos.users;

import java.time.LocalDateTime;

/** Un borrado definitivo de usuario: quien era, a que local pertenecia, quien lo borro y cuando. */
public record UserDeletionAuditDto(
        Long id,
        Long userId,
        String username,
        String fullName,
        String email,
        String role,
        Long storeId,
        String kind,
        String performedBy,
        LocalDateTime createdAt
) {
}
