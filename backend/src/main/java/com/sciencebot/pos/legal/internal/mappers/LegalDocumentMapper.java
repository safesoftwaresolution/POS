package com.sciencebot.pos.legal.internal.mappers;

import com.sciencebot.pos.legal.LegalDocumentDto;
import com.sciencebot.pos.legal.internal.entities.LegalDocumentEntity;
import org.springframework.stereotype.Component;

@Component
public class LegalDocumentMapper {
    public LegalDocumentDto toDto(LegalDocumentEntity entity) {
        if (entity == null) return null;
        return new LegalDocumentDto(
                entity.getId(), entity.getSlug(), entity.getTitle(),
                entity.getContent(), entity.getVersion(), entity.isPublished(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getPublishedAt()
        );
    }
}