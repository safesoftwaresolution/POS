package com.sciencebot.pos.notifications;

/**
 * Punto de entrada que usan OTROS modulos (via {@link NotificationFacade#createNotification})
 * para publicar una notificacion en-app para un local. storeId va explicito (no se toma de
 * TenantContext) porque quien publica suele ser un proceso @Async o un flujo del backoffice sin
 * el store del solicitante en el contexto de la peticion.
 */
public record CreateNotificationCommand(
    Long storeId,
    String type,
    String title,
    String message,
    String linkPath
) {}
