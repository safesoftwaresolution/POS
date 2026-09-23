package com.sciencebot.pos.notifications.internal.services;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.notifications.CreateNotificationCommand;
import com.sciencebot.pos.notifications.NotificationDto;
import com.sciencebot.pos.notifications.NotificationFacade;
import com.sciencebot.pos.notifications.internal.entities.NotificationEntity;
import com.sciencebot.pos.notifications.internal.mappers.NotificationMapper;
import com.sciencebot.pos.notifications.internal.repositories.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationFacade {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    public NotificationServiceImpl(NotificationRepository notificationRepository, NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
    }

    @Override
    public List<NotificationDto> listForCurrentStore() {
        return notificationRepository.findByStoreIdOrderByCreatedAtDesc(requireCurrentStoreId()).stream()
                .map(notificationMapper::toDto).toList();
    }

    @Override
    public long countUnreadForCurrentStore() {
        return notificationRepository.countByStoreIdAndReadFalse(requireCurrentStoreId());
    }

    @Override
    @Transactional
    public NotificationDto markRead(Long id) {
        Long storeId = requireCurrentStoreId();
        NotificationEntity entity = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notificación no encontrada con ID: " + id));
        if (!entity.getStoreId().equals(storeId)) {
            throw new EntityNotFoundException("Notificación no encontrada con ID: " + id);
        }
        entity.setRead(true);
        return notificationMapper.toDto(notificationRepository.save(entity));
    }

    @Override
    @Transactional
    public void markAllReadForCurrentStore() {
        notificationRepository.markAllReadByStoreId(requireCurrentStoreId());
    }

    @Override
    @Transactional
    public void createNotification(CreateNotificationCommand command) {
        NotificationEntity entity = new NotificationEntity();
        entity.setStoreId(command.storeId());
        entity.setType(command.type());
        entity.setTitle(command.title());
        entity.setMessage(command.message());
        entity.setLinkPath(command.linkPath());
        notificationRepository.save(entity);
    }

    private static Long requireCurrentStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeId;
    }
}
