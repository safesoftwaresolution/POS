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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
    record Invoice(String id, String status, String legalStatus, String cufe, String cude, String prefix, Object number,
                           String fullNumber, GovernmentResponse governmentResponse, String qrCodeContent,
                           List<Object> errors) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GovernmentResponse(String code, String message, List<String> errorMessages) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FilesEnvelope(FileLinks files) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FileLinks(String zip, String xml) {}

    private static final int MAX_ZIP_BYTES = 20 * 1024 * 1024;
    private static final int MAX_PDF_BYTES = 10 * 1024 * 1024;
    private static final int MAX_XML_BYTES = 5 * 1024 * 1024;

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

    /**
     * Pide a Alegra que envíe el documento por correo (solo documentos ya aceptados por la DIAN; Alegra adjunta el XML
     * en un zip). Un rechazo del proveedor sale como excepción con su mensaje.
     *
     * <p>OJO: verificado en el sandbox (4 pruebas, la última con entrega confirmada), Alegra entrega el correo a la
     * dirección de {@code replyTo} y no a la de {@code to}, al contrario de lo que dice su documentación. Por eso las
     * dos llevan la dirección del cliente: con el correo de la empresa en {@code replyTo} la factura le llegaría a la
     * empresa y no al cliente. Cuando Alegra lo corrija se puede volver a usar el correo de la empresa como respuesta.
     */
    public boolean sendInvoiceEmail(String providerDocumentId, String to) {
        AlegraAccess access = accessResolver.resolve(null);
        Map<String, Object> body = Map.of(
                "documentType", INVOICE_DOCUMENT,
                "documentId", providerDocumentId,
                "email", Map.of("to", to, "replyTo", to));
        try {
            helper.authorizedPost("/send-email", access)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            throw helper.translate("No se pudo enviar la factura por correo con Alegra", ex);
        }
    }

    /**
     * XML de la factura desde {@code files.xml}. El enlace es temporal (60 min) y puede venir vacío mientras Alegra
     * termina de generarlo: se vuelve a consultar una vez. Solo se sigue un enlace de confianza y se exige XML.
     */
    public byte[] downloadInvoiceXML(String providerDocumentId) {
        AlegraAccess access = accessResolver.resolve(null);
        try {
            String link = null;
            for (int attempt = 0; attempt < 2 && (link == null || link.isBlank()); attempt++) {
                FilesEnvelope envelope = helper.authorizedGet("/invoices/" + providerDocumentId, access)
                        .retrieve()
                        .body(FilesEnvelope.class);
                link = envelope != null && envelope.files() != null ? envelope.files().xml() : null;
            }
            if (link == null || link.isBlank()) {
                throw new IllegalStateException("Alegra todavía no entrega el XML de la factura; intenta de nuevo en un momento");
            }
            byte[] xml = helper.download(link, MAX_XML_BYTES);
            if (!looksLikeXml(xml)) {
                throw new IllegalStateException("El archivo que devolvió Alegra no es un XML válido");
            }
            return xml;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw helper.translate("No se pudo descargar el XML de la factura", ex);
        }
    }

    private static boolean looksLikeXml(byte[] data) {
        int i = 0;
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            i = 3; // BOM UTF-8
        }
        return data.length >= i + 5 && data[i] == '<' && data[i + 1] == '?' && data[i + 2] == 'x'
                && data[i + 3] == 'm' && data[i + 4] == 'l';
    }

    /**
     * PDF de la factura. Alegra lo entrega dentro de {@code files.zip} (junto al XML del documento adjunto); el enlace
     * caduca a los 60 minutos, así que siempre se pide uno fresco con {@code GET /invoices/{id}} y nada se guarda.
     * Se lee solo la entrada .pdf, con tope de tamaño, y se exige que de verdad sea un PDF.
     */
    public byte[] downloadInvoicePDF(String providerDocumentId) {
        AlegraAccess access = accessResolver.resolve(null);
        try {
            FilesEnvelope envelope = helper.authorizedGet("/invoices/" + providerDocumentId, access)
                    .retrieve()
                    .body(FilesEnvelope.class);
            String zipLink = envelope != null && envelope.files() != null ? envelope.files().zip() : null;
            if (zipLink == null || zipLink.isBlank()) {
                throw new IllegalStateException("Alegra no devolvió el paquete con el PDF de la factura");
            }
            return extractPdf(helper.download(zipLink, MAX_ZIP_BYTES));
        } catch (IllegalStateException ex) {
            throw ex; // problema con el contenido o el enlace, no con la conexión
        } catch (Exception ex) {
            throw helper.translate("No se pudo descargar el PDF de la factura", ex);
        }
    }

    /**
     * Saca el PDF del paquete. El zip de Alegra guarda las entradas sin comprimir y con descriptor de datos, que
     * {@code ZipInputStream} no sabe leer; {@code ZipFile} sí (lee el directorio central) pero necesita un archivo,
     * así que el paquete pasa por un temporal que solo el proceso puede leer y que se borra siempre.
     */
    private static byte[] extractPdf(byte[] zipBytes) {
        Path temp = null;
        try {
            temp = Files.createTempFile("alegra-invoice-", ".zip");
            Files.write(temp, zipBytes);
            try (ZipFile zip = new ZipFile(temp.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".pdf")) {
                        continue;
                    }
                    try (InputStream in = zip.getInputStream(entry)) {
                        byte[] pdf = in.readNBytes(MAX_PDF_BYTES + 1);
                        if (pdf.length > MAX_PDF_BYTES) {
                            throw new IllegalStateException("El PDF que devolvió Alegra supera el tamaño permitido");
                        }
                        if (pdf.length < 5 || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F' || pdf[4] != '-') {
                            throw new IllegalStateException("El archivo PDF que devolvió Alegra no es un PDF válido");
                        }
                        return pdf;
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el paquete de Alegra con el PDF de la factura");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // el temporal queda en el directorio temporal del sistema; no impide devolver el PDF
                }
            }
        }
        throw new IllegalStateException("El paquete de Alegra no incluye el PDF de la factura");
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
    InvoiceResult fromInvoice(Invoice invoice, Allocation allocation) {
        if (invoice == null) {
            return InvoiceResult.pending("Alegra respondió sin el documento; se reintentará");
        }
        String legalStatus = invoice.legalStatus();
        String status = invoice.status();

        if ("ACCEPTED".equalsIgnoreCase(legalStatus) || "ACCEPTED_WITH_OBSERVATIONS".equalsIgnoreCase(legalStatus)) {
            complete(allocation);
            String legalNumber = invoice.fullNumber() != null && !invoice.fullNumber().isBlank()
                    ? invoice.fullNumber() : (invoice.prefix() == null ? "" : invoice.prefix()) + invoice.number();
            String code = invoice.cufe() != null ? invoice.cufe() : invoice.cude();
            return InvoiceResult.success(legalNumber, code, invoice.qrCodeContent(), null)
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
    InvoiceResult fromProviderError(BillingProviderException ex, Allocation allocation) {
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
