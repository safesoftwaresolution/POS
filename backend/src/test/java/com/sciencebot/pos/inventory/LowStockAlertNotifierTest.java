package com.sciencebot.pos.inventory;

import com.sciencebot.pos.inventory.internal.services.LowStockAlertNotifier;
import com.sciencebot.pos.notifications.NotificationFacade;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.shared.email.EmailSender;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LowStockAlertNotifierTest {

    @Mock
    private UserFacade userFacade;

    @Mock
    private EmailSender emailSender;

    @Mock
    private NotificationFacade notificationFacade;

    @InjectMocks
    private LowStockAlertNotifier notifier;

    private final ProductDto product = new ProductDto(
            1L, "P1", "123", "Arroz Diana 500g", "Alimentos", null, null,
            BigDecimal.ONE, BigDecimal.TEN, 4, 5, true, null);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void notifyLowStock_EmailsEveryActiveAdminAndSupervisorOfTheStore() {
        UserDto admin = new UserDto(1L, "Ana Admin", "ana", "ana@tienda.com", "ADMINISTRATOR", true, 1L, true);
        UserDto supervisor = new UserDto(2L, "Beto Supervisor", "beto", "beto@tienda.com", "SUPERVISOR", true, 1L, true);
        when(userFacade.listActiveByStoreAndRoles(1L, List.of("ADMINISTRATOR", "SUPERVISOR")))
                .thenReturn(List.of(admin, supervisor));

        notifier.notifyLowStock(1L, product, 4);

        verify(emailSender, times(1)).send(eq("ana@tienda.com"), contains("Arroz Diana 500g"), anyString());
        verify(emailSender, times(1)).send(eq("beto@tienda.com"), contains("Arroz Diana 500g"), anyString());
        verify(notificationFacade, times(1)).createNotification(argThat(cmd ->
                cmd.storeId().equals(1L) && "LOW_STOCK".equals(cmd.type()) && cmd.message().contains("Arroz Diana 500g")));
    }

    @Test
    void notifyLowStock_NotificationFacadeThrows_DoesNotPropagate() {
        doThrow(new IllegalStateException("fallo publicando notificación")).when(notificationFacade).createNotification(any());

        // Es "best-effort": un fallo publicando la notificacion en-app no debe propagarse
        // (rompería la tarea async), igual que un fallo de EmailSender.
        assertDoesNotThrow(() -> notifier.notifyLowStock(1L, product, 4));
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void notifyLowStock_NoRecipients_SendsNothing() {
        when(userFacade.listActiveByStoreAndRoles(1L, List.of("ADMINISTRATOR", "SUPERVISOR")))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> notifier.notifyLowStock(1L, product, 4));

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void notifyLowStock_EmailSenderThrows_DoesNotPropagate() {
        UserDto admin = new UserDto(1L, "Ana Admin", "ana", "ana@tienda.com", "ADMINISTRATOR", true, 1L, true);
        when(userFacade.listActiveByStoreAndRoles(1L, List.of("ADMINISTRATOR", "SUPERVISOR")))
                .thenReturn(List.of(admin));
        doThrow(new IllegalStateException("SMTP caido")).when(emailSender).send(anyString(), anyString(), anyString());

        // Es "best-effort": un fallo de correo nunca debe propagarse (romperia la tarea async).
        assertDoesNotThrow(() -> notifier.notifyLowStock(1L, product, 4));
    }
}
