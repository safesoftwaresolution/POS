package com.sciencebot.pos.notifications.internal.mappers;

import com.sciencebot.pos.notifications.NotificationDto;
import com.sciencebot.pos.notifications.internal.entities.NotificationEntity;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationDto toDto(NotificationEntity entity) {
        if (entity == null) return null;
        return new NotificationDto(
                entity.getId(),
                entity.getType(),
                entity.getTitle(),
                entity.getMessage(),
                entity.getLinkPath(),
                entity.isRead(),
                entity.getCreatedAt()
        );
    }
}
