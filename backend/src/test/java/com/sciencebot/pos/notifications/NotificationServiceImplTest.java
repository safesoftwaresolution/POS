package com.sciencebot.pos.notifications;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.notifications.internal.entities.NotificationEntity;
import com.sciencebot.pos.notifications.internal.mappers.NotificationMapper;
import com.sciencebot.pos.notifications.internal.repositories.NotificationRepository;
import com.sciencebot.pos.notifications.internal.services.NotificationServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceImplTest {

    private static final Long STORE_ID = 1L;

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationMapper notificationMapper;
    @InjectMocks private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setStoreId(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listForCurrentStore_ReturnsStoreNotificationsNewestFirst() {
        NotificationEntity n1 = new NotificationEntity();
        n1.setId(1L);
        n1.setStoreId(STORE_ID);
        when(notificationRepository.findByStoreIdOrderByCreatedAtDesc(STORE_ID)).thenReturn(List.of(n1));
        when(notificationMapper.toDto(n1)).thenReturn(new NotificationDto(1L, "LOW_STOCK", "Stock bajo", "msg", "/inventory", false, null));

        List<NotificationDto> result = notificationService.listForCurrentStore();

        assertEquals(1, result.size());
        assertEquals("LOW_STOCK", result.get(0).type());
    }

    @Test
    void countUnreadForCurrentStore_DelegatesToRepository() {
        when(notificationRepository.countByStoreIdAndReadFalse(STORE_ID)).thenReturn(3L);

        assertEquals(3L, notificationService.countUnreadForCurrentStore());
    }

    @Test
    void markRead_Success_MarksEntityRead() {
        NotificationEntity n1 = new NotificationEntity();
        n1.setId(1L);
        n1.setStoreId(STORE_ID);
        n1.setRead(false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n1));
        when(notificationRepository.save(any())).thenReturn(n1);
        when(notificationMapper.toDto(n1)).thenReturn(new NotificationDto(1L, "LOW_STOCK", "Stock bajo", "msg", "/inventory", true, null));

        NotificationDto result = notificationService.markRead(1L);

        assertTrue(result.read());
        assertTrue(n1.isRead());
    }

    @Test
    void markRead_BelongsToAnotherStore_ThrowsNotFound() {
        NotificationEntity n1 = new NotificationEntity();
        n1.setId(1L);
        n1.setStoreId(99L);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n1));

        assertThrows(EntityNotFoundException.class, () -> notificationService.markRead(1L));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAllReadForCurrentStore_DelegatesToRepository() {
        notificationService.markAllReadForCurrentStore();

        verify(notificationRepository, times(1)).markAllReadByStoreId(STORE_ID);
    }

    @Test
    void createNotification_Success_SavesEntityForGivenStore() {
        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createNotification(new CreateNotificationCommand(5L, "LOW_STOCK", "Título", "Mensaje", "/inventory"));

        verify(notificationRepository, times(1)).save(argThat(e ->
                e.getStoreId().equals(5L) && "LOW_STOCK".equals(e.getType()) && "Título".equals(e.getTitle())));
    }
}
