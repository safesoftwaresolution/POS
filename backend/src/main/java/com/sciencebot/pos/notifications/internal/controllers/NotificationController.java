package com.sciencebot.pos.notifications.internal.controllers;

import com.sciencebot.pos.notifications.NotificationDto;
import com.sciencebot.pos.notifications.NotificationFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notificaciones", description = "Notificaciones en-app del local (stock bajo, revisión de documentos, etc.)")
public class NotificationController {

    private final NotificationFacade notificationFacade;

    public NotificationController(NotificationFacade notificationFacade) {
        this.notificationFacade = notificationFacade;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Listar mis notificaciones", description = "Notificaciones del local en sesión, más recientes primero.")
    public ResponseEntity<List<NotificationDto>> list() {
        return ResponseEntity.ok(notificationFacade.listForCurrentStore());
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Cantidad de notificaciones no leídas del local en sesión")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("count", notificationFacade.countUnreadForCurrentStore()));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Marcar una notificación como leída")
    public ResponseEntity<NotificationDto> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationFacade.markRead(id));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Marcar todas las notificaciones del local en sesión como leídas")
    public ResponseEntity<Void> markAllRead() {
        notificationFacade.markAllReadForCurrentStore();
        return ResponseEntity.noContent().build();
    }
}
