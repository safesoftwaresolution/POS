package com.sciencebot.pos.billing.internal.adapters.alegra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.exception.BillingProviderException;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceClient.Invoice;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceMapper.NoteInput;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceMapper.OriginalInvoice;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator.Allocation;
import com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult;
import com.sciencebot.pos.billing.internal.adapters.dto.CustomerBillingData;
import com.sciencebot.pos.billing.internal.adapters.dto.DebitNoteResult;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import com.sciencebot.pos.billing.internal.adapters.dto.BillingDocumentContext;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.mappers.BillingCanonicalMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Notas crédito (91) y débito (92) en Alegra sobre una factura ya aceptada. Mismas reglas que las facturas: el
 * número y el prefijo los asigna el sistema (las notas no llevan resolución), cada intento viaja con la
 * {@code Idempotency-Key} de su asignación, la decisión sale de {@code legalStatus} y no del HTTP, y tras un
 * resultado final no aceptado se conserva el número y se rota la llave. Si la factura de origen no existe o no está
 * validada se falla antes de gastar un número.
 */
@Component
@ConditionalOnProperty(name = "billing.provider", havingValue = "alegra")
public class AlegraNoteClient {

    private static final Logger log = LoggerFactory.getLogger(AlegraNoteClient.class);

    private static final Pattern LEGAL_NUMBER = Pattern.compile("^([A-Za-z]{1,4})(\\d+)$");

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NoteEnvelope(Invoice creditNote, Invoice debitNote) {
        Invoice document() {
            return creditNote != null ? creditNote : debitNote;
        }
    }

    /** Lo común de una nota crédito y una débito, ya independiente del formato de cada solicitud. */
    private record Draft(String referenceCode, String billNumber, String conceptCode, String observation,
                         String paymentForm, String paymentMethod, LocalDate dueDate, CustomerBillingData customer,
                         List<InvoiceItemData> items) {}

    private final AlegraAccessResolver accessResolver;
    private final AlegraCompanyProvider companyProvider;
    private final AlegraNumberAllocator allocator;
    private final AlegraInvoiceMapper mapper;
    private final AlegraClientHelper helper;
    private final AlegraInvoiceClient invoiceClient;
    private final ElectronicInvoiceRepository invoiceRepository;
    private final SaleFacade saleFacade;
    private final BillingCanonicalMapper canonicalMapper;

    public AlegraNoteClient(AlegraAccessResolver accessResolver,
                            AlegraCompanyProvider companyProvider,
                            AlegraNumberAllocator allocator,
                            AlegraInvoiceMapper mapper,
                            AlegraClientHelper helper,
                            AlegraInvoiceClient invoiceClient,
                            ElectronicInvoiceRepository invoiceRepository,
                            @Lazy SaleFacade saleFacade,
                            BillingCanonicalMapper canonicalMapper) {
        this.accessResolver = accessResolver;
        this.companyProvider = companyProvider;
        this.allocator = allocator;
        this.mapper = mapper;
        this.helper = helper;
        this.invoiceClient = invoiceClient;
        this.invoiceRepository = invoiceRepository;
        this.saleFacade = saleFacade;
        this.canonicalMapper = canonicalMapper;
    }

    public CreditNoteResult emitCreditNote(CreateCreditNoteRequest request, Long storeId) {
        return emitCreditNote(request, storeId, null, null);
    }

    public CreditNoteResult emitCreditNote(CreateCreditNoteRequest request, Long storeId,
                                           BillingDocumentContext context, List<InvoiceItemData> canonicalItems) {
        Draft draft;
        try {
            draft = withCanonicalItems(draftOf(request), canonicalItems);
        } catch (RuntimeException invalid) {
            return CreditNoteResult.error("La nota crédito no se puede enviar: " + invalid.getMessage());
        }
        InvoiceResult result = emit("91", "/credit-notes", draft, storeId, context);
        return withId(new CreditNoteResult(result.isSuccess(), result.legalInvoiceNumber(), result.cufe(), result.qrCode(),
                result.pdfUrl(), result.status(), result.errorMessage(), result.validatedAt()), result.providerDocumentId());
    }

    public DebitNoteResult emitDebitNote(CreateDebitNoteRequest request, Long storeId) {
        return emitDebitNote(request, storeId, null, null);
    }

    public DebitNoteResult emitDebitNote(CreateDebitNoteRequest request, Long storeId,
                                         BillingDocumentContext context, List<InvoiceItemData> canonicalItems) {
        Draft draft;
        try {
            draft = withCanonicalItems(draftOf(request), canonicalItems);
        } catch (RuntimeException invalid) {
            return DebitNoteResult.error("La nota débito no se puede enviar: " + invalid.getMessage());
        }
        InvoiceResult result = emit("92", "/debit-notes", draft, storeId, context);
        return withId(new DebitNoteResult(result.isSuccess(), result.legalInvoiceNumber(), result.cufe(), result.qrCode(),
                result.pdfUrl(), result.status(), result.validatedAt(), result.errorMessage()), result.providerDocumentId());
    }

    /** Los ítems canónicos (los de la venta, ya calculados) reemplazan los de la solicitud cuando vienen. */
    private static Draft withCanonicalItems(Draft draft, List<InvoiceItemData> canonicalItems) {
        if (canonicalItems == null) return draft;
        return new Draft(draft.referenceCode(), draft.billNumber(), draft.conceptCode(), draft.observation(),
                draft.paymentForm(), draft.paymentMethod(), draft.dueDate(), draft.customer(), canonicalItems);
    }

    private static CreditNoteResult withId(CreditNoteResult result, String providerDocumentId) {
        return result.withProviderDocumentId(providerDocumentId);
    }

    private static DebitNoteResult withId(DebitNoteResult result, String providerDocumentId) {
        return result.withProviderDocumentId(providerDocumentId);
    }

    private InvoiceResult emit(String document, String endpoint, Draft draft, Long storeId, BillingDocumentContext context) {
        if (context != null) {
            if (!context.storeId().equals(storeId)) throw new IllegalStateException("El contexto pertenece a otro local");
            var previous = allocator.findAllocation(storeId, context.environment(), document, draft.referenceCode());
            if (previous.isPresent() && "ACCEPTED".equals(previous.get().outcome())) return allocator.receipt(previous.get());
        }
        AlegraAccess access = context == null ? accessResolver.resolve(storeId) : accessResolver.resolveDocumentForEmission(context);

        Optional<ElectronicInvoice> found = invoiceRepository.findByStoreIdAndLegalNumber(access.storeId(), draft.billNumber());
        if (found.isEmpty() || !found.get().isValidated() || found.get().getCufe() == null) {
            return InvoiceResult.error("No existe una factura electrónica validada con el número " + draft.billNumber()
                    + " en esta tienda; solo se puede emitir una nota sobre una factura aceptada por la DIAN");
        }
        ElectronicInvoice invoice = found.get();
        if (context != null && (invoice.getProviderName() == null || !context.equals(invoice.documentContext()))) {
            return InvoiceResult.error("La factura de origen no comparte el contexto fiscal de la nota");
        }
        Matcher legal = LEGAL_NUMBER.matcher(invoice.getLegalNumber() == null ? "" : invoice.getLegalNumber().trim());
        if (!legal.matches()) {
            return InvoiceResult.error("El número de la factura " + invoice.getLegalNumber()
                    + " no tiene el formato prefijo + número que Alegra exige para relacionar la nota");
        }
        LocalDate invoiceDate = invoice.getValidatedAt() != null ? invoice.getValidatedAt().toLocalDate() : LocalDate.now();
        OriginalInvoice original = new OriginalInvoice(invoiceDate, legal.group(1), Long.parseLong(legal.group(2)), invoice.getCufe());

        CustomerBillingData customer = draft.customer() != null ? draft.customer() : customerOfTheSale(invoice.getSaleId());

        AlegraCompany company;
        try {
            company = companyProvider.get(access);
        } catch (BillingProviderException ex) {
            return invoiceClient.fromProviderError(ex, null);
        }

        Allocation allocation = allocator.allocate(access.storeId(), access.environment(), document, draft.referenceCode());
        if ("ACCEPTED".equals(allocation.outcome())) return allocator.receipt(allocation);
        if (allocation.completed() && allocation.outcome() == null && allocation.requestPayload() == null) {
            return InvoiceResult.pending("Asignación histórica sin resultado verificable; requiere conciliación manual");
        }
        if ("REJECTED".equals(allocation.outcome()) || "FAILED".equals(allocation.outcome())) {
            allocation = allocator.rotateIdempotencyKey(allocation.id());
        }
        if (allocation.providerDocumentId() != null) {
            InvoiceResult result = query(endpoint, allocation.providerDocumentId(), access);
            allocator.recordResult(allocation.id(), result);
            return result;
        }

        Map<String, Object> body;
        try {
            body = mapper.toAlegraNotePayload(new NoteInput(draft.conceptCode(), draft.observation(), draft.paymentForm(),
                    draft.paymentMethod(), draft.dueDate(), customer, draft.items()), company, allocation, original);
        } catch (IllegalStateException incomplete) {
            return InvoiceResult.error(incomplete.getMessage());
        }
        return submit(endpoint, access, allocation, invoiceClient.frozenBody(allocation, body));
    }

    private InvoiceResult submit(String endpoint, AlegraAccess access, Allocation allocation, String body) {
        try {
            ResponseEntity<NoteEnvelope> response = helper.authorizedPost(endpoint, access)
                    .header("Idempotency-Key", allocation.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(NoteEnvelope.class);
            Invoice note = response.getBody() != null ? response.getBody().document() : null;
            if (note != null && note.id() != null
                    && "true".equalsIgnoreCase(response.getHeaders().getFirst("Idempotent-Replayed"))) {
                note = refreshed(endpoint, access, note);
            }
            return invoiceClient.fromInvoice(note, allocation);
        } catch (Exception ex) {
            RuntimeException translated = helper.translate("No se pudo emitir la nota en Alegra", ex);
            if (translated instanceof BillingProviderException provider) {
                return invoiceClient.fromProviderError(provider, allocation);
            }
            throw translated;
        }
    }

    public CreditNoteResult retryCreditNote(String reference, BillingDocumentContext context) {
        return creditResult(retry("91", "/credit-notes", reference, context));
    }

    public DebitNoteResult retryDebitNote(String reference, BillingDocumentContext context) {
        return debitResult(retry("92", "/debit-notes", reference, context));
    }

    public CreditNoteResult queryCreditNote(String id, BillingDocumentContext context) {
        return creditResult(query("/credit-notes", id, accessResolver.resolveDocument(context)));
    }

    public DebitNoteResult queryDebitNote(String id, BillingDocumentContext context) {
        return debitResult(query("/debit-notes", id, accessResolver.resolveDocument(context)));
    }

    public byte[] download(String endpoint, String id, BillingDocumentContext context, boolean pdf) {
        if (id == null || id.isBlank()) throw new IllegalStateException("La nota no tiene un identificador verificable en el proveedor");
        AlegraAccess access = accessResolver.resolveDocument(context);
        return pdf ? invoiceClient.downloadPDF(endpoint + "/" + id, access)
                : invoiceClient.downloadXML(endpoint + "/" + id, access);
    }

    private InvoiceResult retry(String document, String endpoint, String reference, BillingDocumentContext context) {
        Allocation allocation = allocator.findAllocation(context.storeId(), context.environment(), document, reference)
                .orElseThrow(() -> new IllegalStateException("La nota no tiene una solicitud fiscal guardada; requiere revisión manual"));
        if ("ACCEPTED".equals(allocation.outcome())) return allocator.receipt(allocation);
        AlegraAccess access = accessResolver.resolveDocumentForEmission(context);
        if (allocation.providerDocumentId() != null && !"REJECTED".equals(allocation.outcome()) && !"FAILED".equals(allocation.outcome())) {
            InvoiceResult result = query(endpoint, allocation.providerDocumentId(), access);
            allocator.recordResult(allocation.id(), result);
            return result;
        }
        if (allocation.completed()) {
            InvoiceResult receipt = allocator.receipt(allocation);
            return receipt != null ? receipt : InvoiceResult.pending("Resultado histórico incierto; requiere revisión manual");
        }
        if (allocation.requestPayload() == null) return InvoiceResult.pending("La nota no tiene contenido recuperable; requiere revisión manual");
        return submit(endpoint, access, allocation, allocation.requestPayload());
    }

    private InvoiceResult query(String endpoint, String id, AlegraAccess access) {
        try {
            NoteEnvelope envelope = helper.authorizedGet(endpoint + "/" + id, access).retrieve().body(NoteEnvelope.class);
            return invoiceClient.fromInvoice(envelope != null ? envelope.document() : null, null).withProviderDocumentId(id);
        } catch (Exception ex) {
            return InvoiceResult.pending("No se pudo conciliar la nota; no se emitirá otra: " +
                    helper.translate("Error de consulta de nota", ex).getMessage()).withProviderDocumentId(id);
        }
    }

    private static CreditNoteResult creditResult(InvoiceResult result) {
        return new CreditNoteResult(result.isSuccess(), result.legalInvoiceNumber(), result.cufe(), result.qrCode(),
                result.pdfUrl(), result.status(), result.errorMessage(), result.validatedAt(), result.providerDocumentId());
    }

    private static DebitNoteResult debitResult(InvoiceResult result) {
        return new DebitNoteResult(result.isSuccess(), result.legalInvoiceNumber(), result.cufe(), result.qrCode(),
                result.pdfUrl(), result.status(), result.validatedAt(), result.errorMessage(), result.providerDocumentId());
    }

    /** Un replay devuelve la respuesta guardada del primer intento: se lee el estado actual de la nota. */
    private Invoice refreshed(String endpoint, AlegraAccess access, Invoice stored) {
        try {
            NoteEnvelope envelope = helper.authorizedGet(endpoint + "/" + stored.id(), access).retrieve().body(NoteEnvelope.class);
            return envelope != null && envelope.document() != null ? envelope.document() : stored;
        } catch (Exception ex) {
            log.warn("No se pudo refrescar la nota {} tras un replay: {}", stored.id(), ex.getMessage());
            return stored;
        }
    }

    /** El cliente de la nota es el de la venta original (la DIAN exige el mismo cliente); sin venta, consumidor final. */
    private CustomerBillingData customerOfTheSale(Long saleId) {
        try {
            if (saleId == null) {
                return null;
            }
            Optional<SaleDto> sale = saleFacade.getById(saleId);
            return sale.isPresent() ? canonicalMapper.toInvoiceRequest(sale.get()).customer() : null;
        } catch (RuntimeException ex) {
            log.warn("No se pudo leer el cliente de la venta {} para la nota: {}", saleId, ex.getMessage());
            return null;
        }
    }

    private static Draft draftOf(CreateCreditNoteRequest request) {
        var payment = request.paymentDetails() != null && !request.paymentDetails().isEmpty() ? request.paymentDetails().get(0) : null;
        CreateCreditNoteRequest.CustomerCreditNoteRequest c = request.customer();
        CustomerBillingData customer = c == null ? null : new CustomerBillingData(c.identificationDocumentCode(),
                c.identification(), c.dv(), c.legalOrganizationCode(), c.tributeCode(), c.responsibilities(), c.names(),
                c.company(), c.tradeName(), c.email(), c.phone(), c.address(), c.countryCode(), c.municipalityCode());
        List<InvoiceItemData> items = new java.util.ArrayList<>();
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("no tiene ítems");
        }
        for (var item : request.items()) {
            var tax = item.taxes() != null && !item.taxes().isEmpty() ? item.taxes().get(0) : null;
            items.add(item(item.codeReference(), item.name(), item.quantity(), item.price(), item.discountRate(),
                    item.unitMeasureCode(), item.standardCode(), tax == null ? null : tax.code(), tax == null ? null : tax.rate()));
        }
        return new Draft(request.referenceCode(), request.billNumber(), request.correctionConceptCode(), request.observation(),
                payment == null ? "1" : payment.paymentForm(), payment == null ? "10" : payment.paymentMethodCode(), null,
                customer, items);
    }

    private static Draft draftOf(CreateDebitNoteRequest request) {
        var payment = request.paymentDetails() != null && !request.paymentDetails().isEmpty() ? request.paymentDetails().get(0) : null;
        CreateDebitNoteRequest.CustomerDebitNoteRequest c = request.customer();
        CustomerBillingData customer = c == null ? null : new CustomerBillingData(c.identificationDocumentCode(),
                c.identification(), c.dv(), c.legalOrganizationCode(), c.tributeCode(), c.responsibilities(), c.names(),
                c.company(), c.tradeName(), c.email(), c.phone(), c.address(), c.countryCode(), c.municipalityCode());
        List<InvoiceItemData> items = new java.util.ArrayList<>();
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("no tiene ítems");
        }
        for (var item : request.items()) {
            var tax = item.taxes() != null && !item.taxes().isEmpty() ? item.taxes().get(0) : null;
            items.add(item(item.codeReference(), item.name(), item.quantity(), item.price(), item.discountRate(),
                    item.unitMeasureCode(), item.standardCode(), tax == null ? null : tax.code(), tax == null ? null : tax.rate()));
        }
        LocalDate due = payment != null && payment.dueDate() != null && !payment.dueDate().isBlank()
                ? LocalDate.parse(payment.dueDate().trim()) : null;
        return new Draft(request.referenceCode(), request.billNumber(), request.correctionConceptCode(), request.observation(),
                payment == null ? "1" : payment.paymentForm(), payment == null ? "10" : payment.paymentMethodCode(), due,
                customer, items);
    }

    private static InvoiceItemData item(String code, String name, String quantity, String price, String discountRate,
                                        String unitCode, String standardCode, String taxCode, String taxRate) {
        if (quantity == null || price == null) {
            throw new IllegalArgumentException("un ítem no tiene cantidad o precio");
        }
        BigDecimal qty = new BigDecimal(quantity.trim());
        BigDecimal unitPrice = new BigDecimal(price.trim());
        BigDecimal rate = taxRate == null || taxRate.isBlank() ? BigDecimal.ZERO : new BigDecimal(taxRate.trim());
        BigDecimal discount = discountRate == null || discountRate.isBlank() ? BigDecimal.ZERO : new BigDecimal(discountRate.trim());
        return new InvoiceItemData(code, name, qty, unitPrice, rate, qty.multiply(unitPrice),
                unitCode == null || unitCode.isBlank() ? "94" : unitCode.trim(),
                standardCode == null || standardCode.isBlank() ? "999" : standardCode.trim(),
                taxCode == null || taxCode.isBlank() ? "01" : taxCode.trim(), discount, rate.signum() == 0);
    }
}
