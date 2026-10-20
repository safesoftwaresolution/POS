package com.sciencebot.pos.billing.internal.listeners;

import com.sciencebot.pos.billing.internal.services.InvoiceService;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.sales.SaleCompletedEvent;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener de eventos de dominio en el módulo de facturación (Billing).
 * Ejecuta la emisión a la DIAN de manera asíncrona una vez que la venta se ha confirmado
 * en la base de datos (AFTER_COMMIT), eliminando el bloqueo del hilo del cajero.
 */
@Component
public class ElectronicInvoiceEventListener {

    private static final Logger log = LoggerFactory.getLogger(ElectronicInvoiceEventListener.class);

    private final InvoiceService invoiceService;
    private final SaleFacade saleFacade;

    public ElectronicInvoiceEventListener(InvoiceService invoiceService, @Lazy SaleFacade saleFacade) {
        this.invoiceService = invoiceService;
        this.saleFacade = saleFacade;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSaleCompleted(SaleCompletedEvent event) {
        if (!event.sendToFactus()) {
            log.debug("Venta ID {} no requiere emisión automática a la DIAN", event.saleId());
            return;
        }

        log.info("Procesando emisión asíncrona de factura electrónica para la venta ID: {}", event.saleId());

        Long previousStoreId = TenantContext.getStoreId();
        try {
            if (event.storeId() != null) {
                TenantContext.setStoreId(event.storeId());
            }

            SaleDto sale = saleFacade.getById(event.saleId()).orElse(null);
            if (sale == null) {
                log.warn("No se encontró la venta ID {} para facturar electrónicamente", event.saleId());
                return;
            }

            invoiceService.processElectronicInvoice(sale);
        } catch (Exception ex) {
            log.error("Fallo inesperado al procesar factura electrónica para venta ID {}: {}", event.saleId(), ex.getMessage(), ex);
        } finally {
            if (previousStoreId != null) {
                TenantContext.setStoreId(previousStoreId);
            } else {
                TenantContext.clear();
            }
        }
    }
}
