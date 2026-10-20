package com.sciencebot.pos.support;

import java.time.LocalDateTime;

public record SupportTicketDto(
    Long id, String ticketNumber, String type, String priority, String status,
    String title, String description, String contactName, String contactEmail,
    String contactPhone, Long storeId, String systemInfo, String resolutionNotes,
    LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime resolvedAt
) {}