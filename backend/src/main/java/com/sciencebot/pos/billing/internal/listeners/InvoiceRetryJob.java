package com.sciencebot.pos.billing.internal.listeners;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.billing.internal.services.InvoiceService;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reintenta solo las facturas que quedaron PENDING o en ERROR (timeouts, caídas del proveedor, reserva vencida),
 * con la espera creciente que dejó programada la emisión anterior. Nunca reintenta un rechazo de la DIAN (exige
 * corregir datos) ni una factura ya validada. Cada factura se emite con su propio local en contexto y un fallo no
 * detiene el resto del lote. No necesita lock distribuido: la reserva (lease) en la fila evita la doble emisión
 * aunque haya varias instancias.
 */
@Component
@ConditionalOnProperty(name = "billing.retry.enabled", havingValue = "true", matchIfMissing = true)
public class InvoiceRetryJob {

    private static final Logger log = LoggerFactory.getLogger(InvoiceRetryJob.class);

    private final ElectronicInvoiceRepository repository;
    private final StoreBillingConfigRepository configRepository;
    private final SaleFacade saleFacade;
    private final InvoiceService invoiceService;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration lease;

    public InvoiceRetryJob(ElectronicInvoiceRepository repository,
                           StoreBillingConfigRepository configRepository,
                           @Lazy SaleFacade saleFacade,
                           InvoiceService invoiceService,
                           @Value("${billing.retry.batch-size:20}") int batchSize,
                           @Value("${billing.retry.max-attempts:8}") int maxAttempts,
                           @Value("${billing.emission.lease:PT5M}") Duration lease) {
        this.repository = repository;
        this.configRepository = configRepository;
        this.saleFacade = saleFacade;
        this.invoiceService = invoiceService;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.lease = lease;
    }

    @Scheduled(fixedDelayString = "${billing.retry.interval:60000}",
            initialDelayString = "${billing.retry.initial-delay:60000}")
    public void scheduledRun() {
        try {
            retryDue(LocalDateTime.now());
        } catch (Exception ex) {
            log.warn("Reintento automático de facturas omitido: {}", ex.getMessage());
        }
    }

    /** @return cantidad de facturas que se volvieron a emitir en esta pasada */
    public int retryDue(LocalDateTime now) {
        List<ElectronicInvoice> due = repository.findDueForRetry(
                now, now.minus(lease), maxAttempts, PageRequest.of(0, batchSize));
        int retried = 0;
        for (ElectronicInvoice invoice : due) {
            if (retryOne(invoice)) {
                retried++;
            }
        }
        if (retried > 0) {
            log.info("Reintento automático: {} de {} facturas pendientes se volvieron a emitir", retried, due.size());
        }
        return retried;
    }

    private boolean retryOne(ElectronicInvoice invoice) {
        Long previousStoreId = TenantContext.getStoreId();
        try {
            if (!billingIsActive(invoice.getStoreId())) {
                log.debug("Local {} sin facturación activa: se omite la factura de la venta {}",
                        invoice.getStoreId(), invoice.getSaleId());
                return false;
            }
            TenantContext.setStoreId(invoice.getStoreId());
            SaleDto sale = saleFacade.getById(invoice.getSaleId()).orElse(null);
            if (sale == null) {
                log.warn("No se encontró la venta ID {} para reintentar su factura electrónica", invoice.getSaleId());
                return false;
            }
            invoiceService.processElectronicInvoice(sale);
            return true;
        } catch (Exception ex) {
            log.warn("Falló el reintento de la factura de la venta {}: {}", invoice.getSaleId(), ex.getMessage());
            return false;
        } finally {
            if (previousStoreId != null) {
                TenantContext.setStoreId(previousStoreId);
            } else {
                TenantContext.clear();
            }
        }
    }

    private boolean billingIsActive(Long storeId) {
        return storeId != null && configRepository.findById(storeId)
                .map(config -> config.getStatus() == BillingActivationStatus.ACTIVE)
                .orElse(false);
    }
}
