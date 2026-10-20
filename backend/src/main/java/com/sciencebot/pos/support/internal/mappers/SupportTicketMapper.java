package com.sciencebot.pos.support.internal.mappers;

import com.sciencebot.pos.support.SupportTicketDto;
import com.sciencebot.pos.support.internal.entities.SupportTicketEntity;
import org.springframework.stereotype.Component;

@Component
public class SupportTicketMapper {
    public SupportTicketDto toDto(SupportTicketEntity e) {
        if (e == null) return null;
        return new SupportTicketDto(
                e.getId(), e.getTicketNumber(), e.getType(), e.getPriority(), e.getStatus(),
                e.getTitle(), e.getDescription(), e.getContactName(), e.getContactEmail(),
                e.getContactPhone(), e.getStoreId(), e.getSystemInfo(), e.getResolutionNotes(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getResolvedAt()
        );
    }
}