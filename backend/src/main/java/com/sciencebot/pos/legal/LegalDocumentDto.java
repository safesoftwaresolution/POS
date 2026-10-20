package com.sciencebot.pos.legal;

import java.time.LocalDateTime;

public record LegalDocumentDto(
    Long id,
    String slug,
    String title,
    String content,
    String version,
    boolean published,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime publishedAt
) {}