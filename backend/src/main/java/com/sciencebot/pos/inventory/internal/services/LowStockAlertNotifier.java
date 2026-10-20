package com.sciencebot.pos.inventory.internal.services;

import com.sciencebot.pos.notifications.CreateNotificationCommand;
import com.sciencebot.pos.notifications.NotificationFacade;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.shared.email.EmailSender;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Avisa por correo a los ADMINISTRATOR/SUPERVISOR de un local y publica una notificacion en-app
 * cuando el stock de un producto cruza su minStock hacia abajo. Se dispara desde
 * InventoryServiceImpl justo despues de aplicar el movimiento que causo la caida, en el mismo
 * instante en que ocurre (no depende de que alguien abra el reporte de stock manualmente).
 *
 * Es @Async y nunca deja escapar una excepcion: un fallo de SMTP, de resolucion de destinatarios
 * o de publicacion de la notificacion NO debe revertir ni retrasar la venta/compra/ajuste que lo
 * disparo.
 */
@Component
public class LowStockAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(LowStockAlertNotifier.class);
    private static final List<String> RECIPIENT_ROLES = List.of("ADMINISTRATOR", "SUPERVISOR");

    private final UserFacade userFacade;
    private final EmailSender emailSender;
    private final NotificationFacade notificationFacade;

    LowStockAlertNotifier(UserFacade userFacade, EmailSender emailSender, NotificationFacade notificationFacade) {
        this.userFacade = userFacade;
        this.emailSender = emailSender;
        this.notificationFacade = notificationFacade;
    }

    @Async
    public void notifyLowStock(Long storeId, ProductDto product, int newStock) {
        try {
            notificationFacade.createNotification(new CreateNotificationCommand(
                    storeId,
                    "LOW_STOCK",
                    "Stock bajo: " + product.name(),
                    "El producto " + product.name() + " (código " + product.internalCode() + ") llegó a "
                            + newStock + " unidades, igual o por debajo de su stock mínimo (" + product.minStock() + ").",
                    "/inventory"
            ));

            List<UserDto> recipients = userFacade.listActiveByStoreAndRoles(storeId, RECIPIENT_ROLES);
            if (recipients.isEmpty()) {
                return;
            }

            String subject = "Stock bajo: " + product.name();
            String body = "<p>El producto <strong>" + product.name() + "</strong> "
                    + "(código " + product.internalCode() + ") llegó a <strong>" + newStock + "</strong> unidades, "
                    + "igual o por debajo de su stock mínimo configurado (" + product.minStock() + ").</p>"
                    + "<p>Es recomendable reabastecerlo pronto para no quedarte sin existencias.</p>";

            for (UserDto recipient : recipients) {
                if (recipient.email() != null && !recipient.email().isBlank()) {
                    emailSender.send(recipient.email(), subject, body);
                }
            }
        } catch (Exception e) {
            // Best-effort: la alerta es una conveniencia, no debe afectar el flujo de inventario
            // que la disparo ni propagarse como una tarea asincrona fallida sin explicacion.
            log.warn("No se pudo enviar la alerta de stock bajo para el producto {} (local {}): {}",
                    product.id(), storeId, e.getMessage(), e);
        }
    }
}
