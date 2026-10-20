package com.sciencebot.pos.notifications;

import java.util.List;

public interface NotificationFacade {

    /** Notificaciones del local en contexto (TenantContext), mas recientes primero. */
    List<NotificationDto> listForCurrentStore();

    /** Cantidad de notificaciones no leidas del local en contexto. */
    long countUnreadForCurrentStore();

    /** Marca una notificacion del local en contexto como leida. */
    NotificationDto markRead(Long id);

    /** Marca todas las notificaciones del local en contexto como leidas. */
    void markAllReadForCurrentStore();

    /** Publica una notificacion para el local indicado en el command. Usado por otros modulos. */
    void createNotification(CreateNotificationCommand command);
}
