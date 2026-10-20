package com.sciencebot.pos.notifications;

import java.time.LocalDateTime;

public record NotificationDto(
    Long id,
    String type,
    String title,
    String message,
    String linkPath,
    boolean read,
    LocalDateTime createdAt
) {}
