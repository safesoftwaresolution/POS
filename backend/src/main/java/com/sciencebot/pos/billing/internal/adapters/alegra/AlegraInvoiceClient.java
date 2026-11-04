package com.sciencebot.pos.billing.internal.adapters.alegra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingProviderException;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator.Allocation;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Emite facturas en Alegra. El número y la resolución los asigna el sistema ({@link AlegraNumberAllocator}) y
 * cada intento viaja con la {@code Idempotency-Key} de su asignación: reintentar tras un timeout reenvía el mismo
 * número y la misma llave, así Alegra devuelve el documento ya recibido en lugar de crear otro. Tras un resultado
 * final no aceptado (rechazo, fallo, datos inválidos) el número se conserva y la llave se rota, porque el cuerpo
 * puede cambiar. Nunca se confía en el 201: la decisión sale de {@code legalStatus} y {@code status}.
 */
@Component
@ConditionalOnProperty(name = "billing.provider", havingValue = "alegra")
public class AlegraInvoiceClient {

    private static final Logger log = LoggerFactory.getLogger(AlegraInvoiceClient.class);

    private static final String INVOICE_DOCUMENT = "01";
    private static final String WAITING = "En espera de respuesta de la DIAN";

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Envelope(Invoice invoice) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Invoice(String id, String status, String legalStatus, String cufe, String prefix, Object number,
                           String fullNumber, GovernmentResponse governmentResponse, String qrCodeContent,
                           List<Object> errors) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GovernmentResponse(String code, String message, List<String> errorMessages) {}

    private final AlegraAccessResolver accessResolver;
    private final AlegraCompanyProvider companyProvider;
    private final AlegraNumberAllocator allocator;
    private final AlegraInvoiceMapper mapper;
    private final AlegraClientHelper helper;

    public AlegraInvoiceClient(AlegraAccessResolver accessResolver,
                               AlegraCompanyProvider companyProvider,
                               AlegraNumberAllocator allocator,
                               AlegraInvoiceMapper mapper,
                               AlegraClientHelper helper) {
        this.accessResolver = accessResolver;
        this.companyProvider = companyProvider;
        this.allocator = allocator;
        this.mapper = mapper;
        this.helper = helper;
    }

    /**
     * Emite la factura. Un local sin acceso configurado lanza {@code BillingNotEnabledException} (lo trata el
     * servicio); cualquier respuesta de Alegra o fallo de red sale como un {@link InvoiceResult}.
     */
    public InvoiceResult emitInvoice(InvoiceRequest request) {
        String invalid = invalidRequest(request);
        if (invalid != null) {
            return InvoiceResult.error(invalid); // antes de asignar número: no se gasta un consecutivo en algo que no puede enviarse
        }
        Long storeId = request.storeId() != null ? request.storeId() : TenantContext.getStoreId();
        AlegraAccess access = accessResolver.resolve(storeId);

        AlegraCompany company;
        try {
            company = companyProvider.get(access);
        } catch (BillingProviderException ex) {
            return fromProviderError(ex, null);
        }

        String reference = (request.invoiceNumber() != null ? request.invoiceNumber() : "FACT") + "-" + request.saleId();
        Allocation allocation = allocator.allocate(access.storeId(), access.environment(), INVOICE_DOCUMENT, reference);
        if (allocation.completed()) {
            allocation = allocator.rotateIdempotencyKey(allocation.id());
        }

        Map<String, Object> body;
        try {
            body = mapper.toAlegraPayload(request, company, allocation);
        } catch (IllegalStateException incompleteRange) {
            return InvoiceResult.error(incompleteRange.getMessage());
        }

        try {
            ResponseEntity<Envelope> response = helper.authorizedPost("/invoices", access)
                    .header("Idempotency-Key", allocation.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(Envelope.class);
            Envelope envelope = response.getBody();
            Invoice invoice = envelope != null ? envelope.invoice() : null;
            if (invoice != null && invoice.id() != null
                    && "true".equalsIgnoreCase(response.getHeaders().getFirst("Idempotent-Replayed"))) {
                // Un replay devuelve la respuesta GUARDADA del primer intento: el documento pudo cambiar de estado.
                invoice = refreshed(access, invoice);
            }
            return fromInvoice(invoice, allocation);
        } catch (Exception ex) {
            RuntimeException translated = helper.translate("No se pudo emitir la factura en Alegra", ex);
            if (translated instanceof BillingProviderException provider) {
                return fromProviderError(provider, allocation);
            }
            throw translated;
        }
    }

    /** Lee un documento ya recibido por Alegra (para conciliar una factura pendiente en vez de emitirla de nuevo). */
    public InvoiceResult queryInvoiceByProviderId(String providerDocumentId) {
        AlegraAccess access = accessResolver.resolve(null);
        try {
            Envelope envelope = helper.authorizedGet("/invoices/" + providerDocumentId, access)
                    .retrieve()
                    .body(Envelope.class);
            return fromInvoice(envelope != null ? envelope.invoice() : null, null);
        } catch (Exception ex) {
            RuntimeException translated = helper.translate("No se pudo consultar la factura en Alegra", ex);
            if (translated instanceof BillingProviderException provider) {
                if (provider.getUpstreamStatus() == 404) {
                    // Alegra confirma que ese documento no existe: no hay nada que conciliar y puede emitirse de nuevo.
                    return InvoiceResult.error("El documento " + providerDocumentId + " ya no existe en Alegra")
                            .withProviderDocumentId(providerDocumentId);
                }
                return fromProviderError(provider, null);
            }
            throw translated;
        }
    }

    /** Estado actual del documento (GET); si no se puede leer se usa el guardado, que nunca es peor que no saber nada. */
    private Invoice refreshed(AlegraAccess access, Invoice stored) {
        try {
            Envelope envelope = helper.authorizedGet("/invoices/" + stored.id(), access).retrieve().body(Envelope.class);
            return envelope != null && envelope.invoice() != null ? envelope.invoice() : stored;
        } catch (Exception ex) {
            log.warn("No se pudo refrescar el documento {} tras un replay: {}", stored.id(), ex.getMessage());
            return stored;
        }
    }

    private static String invalidRequest(InvoiceRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            return "La factura no tiene ítems";
        }
        for (var item : request.items()) {
            if (item.quantity() == null || item.unitPrice() == null) {
                return "Un ítem de la factura no tiene cantidad o precio";
            }
        }
        return null;
    }

    /** La respuesta de Alegra manda: el 201 solo dice que el documento se recibió, no que la DIAN lo aceptó. */
    private InvoiceResult fromInvoice(Invoice invoice, Allocation allocation) {
        if (invoice == null) {
            return InvoiceResult.pending("Alegra respondió sin el documento; se reintentará");
        }
        String legalStatus = invoice.legalStatus();
        String status = invoice.status();

        if ("ACCEPTED".equalsIgnoreCase(legalStatus) || "ACCEPTED_WITH_OBSERVATIONS".equalsIgnoreCase(legalStatus)) {
            complete(allocation);
            String legalNumber = invoice.fullNumber() != null && !invoice.fullNumber().isBlank()
                    ? invoice.fullNumber() : (invoice.prefix() == null ? "" : invoice.prefix()) + invoice.number();
            return InvoiceResult.success(legalNumber, invoice.cufe(), invoice.qrCodeContent(), null)
                    .withProviderDocumentId(invoice.id());
        }
        if ("REJECTED".equalsIgnoreCase(legalStatus)) {
            complete(allocation);
            return InvoiceResult.rejected(reasons(invoice, "La DIAN rechazó la factura")).withProviderDocumentId(invoice.id());
        }
        if ("FAILED".equalsIgnoreCase(status)) {
            complete(allocation);
            return InvoiceResult.error(reasons(invoice, "Alegra no pudo procesar la factura")).withProviderDocumentId(invoice.id());
        }
        // REGISTERED, WAITING_RESPONSE o SENT sin legalStatus: la DIAN aún no responde.
        return InvoiceResult.pending(WAITING).withProviderDocumentId(invoice.id());
    }

    /** Traduce un fallo HTTP al resultado: qué se corrige, qué se reintenta y cuándo cambia la llave. */
    private InvoiceResult fromProviderError(BillingProviderException ex, Allocation allocation) {
        int status = ex.getUpstreamStatus();
        log.warn("Alegra respondió con un error al emitir/consultar una factura (estado {}): {}", status, ex.getMessage());
        if (status == 400) {
            // Datos inválidos: nada se creó, pero el cuerpo debe corregirse, así que el próximo intento usa otra llave.
            complete(allocation);
            return InvoiceResult.rejected(ex.getMessage());
        }
        if (status == 422 && allocation != null) {
            allocator.rotateIdempotencyKey(allocation.id());
            return InvoiceResult.pending("La llave de idempotencia ya se usó con otro contenido; se reintentará con una nueva");
        }
        if (ex.isAuthenticationError() || ex.isTemporary() || status == 409) {
            return InvoiceResult.pending(ex.getMessage());
        }
        return InvoiceResult.error(ex.getMessage());
    }

    private void complete(Allocation allocation) {
        if (allocation != null) {
            allocator.markCompleted(allocation.id());
        }
    }

    private static String reasons(Invoice invoice, String fallback) {
        GovernmentResponse government = invoice.governmentResponse();
        if (government != null && government.errorMessages() != null && !government.errorMessages().isEmpty()) {
            return String.join("; ", government.errorMessages());
        }
        if (government != null && government.message() != null && !government.message().isBlank()) {
            return government.message();
        }
        if (invoice.errors() != null && !invoice.errors().isEmpty()) {
            return invoice.errors().stream().map(String::valueOf).reduce((a, b) -> a + "; " + b).orElse(fallback);
        }
        return fallback;
    }
}
