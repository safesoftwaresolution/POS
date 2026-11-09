package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
import com.sciencebot.pos.billing.dto.CreditNoteDTO;
import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import com.sciencebot.pos.billing.internal.adapters.CreditNoteProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult;
import com.sciencebot.pos.billing.internal.adapters.dto.BillingDocumentContext;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import com.sciencebot.pos.billing.internal.entities.ElectronicCreditNote;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.mappers.ElectronicCreditNoteMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicCreditNoteRepository;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.shared.validation.Preconditions;
import com.sciencebot.pos.sales.SaleFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import com.sciencebot.pos.billing.internal.mappers.BillingCanonicalMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CreditNoteServiceImpl implements CreditNoteService {

    private static final Logger log = LoggerFactory.getLogger(CreditNoteServiceImpl.class);

    private final ElectronicCreditNoteRepository repository;
    private final ElectronicCreditNoteMapper mapper;
    private final ElectronicInvoiceRepository invoiceRepository;
    private final SaleFacade saleFacade;
    private final CreditNoteEmissionTransactions emissionTransactions;
    private final Map<String, CreditNoteProvider> providersMap;
    private final BillingCanonicalMapper canonicalMapper;

    @Value("${billing.provider:alegra}")
    private String activeProviderName;

    public CreditNoteServiceImpl(
            ElectronicCreditNoteRepository repository,
            ElectronicCreditNoteMapper mapper,
            ElectronicInvoiceRepository invoiceRepository,
            @Lazy SaleFacade saleFacade,
            CreditNoteEmissionTransactions emissionTransactions,
            List<CreditNoteProvider> providers
    ) {
        this(repository, mapper, invoiceRepository, saleFacade, emissionTransactions, providers, null);
    }

    @Autowired
    public CreditNoteServiceImpl(ElectronicCreditNoteRepository repository, ElectronicCreditNoteMapper mapper,
            ElectronicInvoiceRepository invoiceRepository, @Lazy SaleFacade saleFacade,
            CreditNoteEmissionTransactions emissionTransactions, List<CreditNoteProvider> providers,
            BillingCanonicalMapper canonicalMapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.invoiceRepository = invoiceRepository;
        this.saleFacade = saleFacade;
        this.emissionTransactions = emissionTransactions;
        this.canonicalMapper = canonicalMapper;
        this.providersMap = providers.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderName().toLowerCase(),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    @Override
    public CreditNoteDTO createAndValidateCreditNote(CreateCreditNoteRequest request) {
        return emit(request, null);
    }

    /**
     * Emision en tres pasos: reservar (transaccion corta con lock) -> llamar al proveedor SIN transaccion ni lock ->
     * registrar el resultado (transaccion corta). Emisiones simultaneas de la misma referencia llaman al proveedor una vez.
     */
    private CreditNoteDTO emit(CreateCreditNoteRequest request, Long saleId) {
        return emit(request, saleId, null);
    }

    private CreditNoteDTO emit(CreateCreditNoteRequest request, Long saleId, List<InvoiceItemData> canonicalItems) {
        Objects.requireNonNull(request, "La solicitud de nota crédito no puede ser nula");
        if (request.referenceCode() == null || request.referenceCode().isBlank()) {
            throw new IllegalArgumentException("El reference_code es obligatorio para la nota crédito");
        }

        // Sin local en la sesión no se emite: antes se asumía el local 1 y se facturaba con el equivocado.
        Long storeId = requireStoreId();
        // Paso A: reserva. Si la nota ya está validada o la emite otro hilo, se devuelve su estado actual.
        CreditNoteEmissionTransactions.Reservation reservation = reserve(request, saleId, storeId);
        if (reservation.outcome() != ElectronicInvoice.ReserveOutcome.RESERVED) {
            log.info("La nota crédito con referencia {} no se emite ahora: {}", request.referenceCode(), reservation.outcome());
            return reservation.note();
        }

        CreditNoteProvider provider = resolveProvider(reservation.context());

        // Paso B: proveedor, sin transacción abierta.
        CreditNoteResult result;
        try {
            if ("alegra".equalsIgnoreCase(provider.getProviderName())) {
                if (reservation.context() == null) throw new IllegalStateException("La nota no tiene su contexto fiscal original; requiere revisión manual");
                result = reservation.reconcileProviderDocumentId() != null
                        ? provider.retryCreditNote(request.referenceCode(), reservation.context())
                        : provider.emitCreditNote(request, storeId, reservation.context(), canonicalItems);
            } else {
                result = provider.emitCreditNote(request, storeId);
            }
        } catch (Exception ex) {
            log.error("Error al procesar emisión de nota crédito con proveedor {}: {}", provider.getProviderName(), ex.getMessage(), ex);
            result = CreditNoteResult.error("Error en proveedor " + provider.getProviderName() + ": " + ex.getMessage());
        }

        // Paso C: registrar el resultado.
        return emissionTransactions.complete(request.referenceCode(), storeId, result);
    }

    private CreditNoteEmissionTransactions.Reservation reserve(CreateCreditNoteRequest request, Long saleId, Long storeId) {
        try {
            return emissionTransactions.reserve(request, saleId, storeId);
        } catch (DataIntegrityViolationException concurrentInsert) {
            // Dos hilos crearon la misma referencia a la vez: el segundo ya la encuentra y se encola detrás del lock.
            return emissionTransactions.reserve(request, saleId, storeId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CreditNoteDTO> getById(Long id) {
        Long storeId = TenantContext.getStoreId();
        if (id == null || storeId == null) return Optional.empty();
        return repository.findById(id)
                .filter(n -> storeId.equals(n.getStoreId()))
                .map(mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CreditNoteDTO> getByReferenceCode(String referenceCode) {
        Long storeId = TenantContext.getStoreId();
        if (referenceCode == null || referenceCode.isBlank() || storeId == null) return Optional.empty();
        return repository.findByReferenceCodeAndStoreId(referenceCode.trim(), storeId)
                .map(mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CreditNoteDTO> listCreditNotes(Pageable pageable, String status) {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            return Page.empty();
        }

        Page<ElectronicCreditNote> page;
        if (status != null && !status.isBlank()) {
            try {
                ElectronicInvoiceStatus statusEnum = ElectronicInvoiceStatus.valueOf(status.trim().toUpperCase());
                page = repository.findByStoreIdAndStatus(storeId, statusEnum, pageable);
            } catch (IllegalArgumentException ex) {
                return Page.empty();
            }
        } else {
            page = repository.findByStoreId(storeId, pageable);
        }
        return page.map(mapper::toDto);
    }

    @Override
    public byte[] downloadCreditNotePDF(Long id) {
        Long storeId = requireStoreId();
        ElectronicCreditNote note = repository.findById(id)
                .filter(n -> storeId.equals(n.getStoreId()))
                .orElseThrow(() -> new IllegalArgumentException("Nota crédito no encontrada con ID: " + id));

        if (!note.isValidated() || note.getCreditNoteNumber() == null) {
            throw new IllegalStateException("La nota crédito no ha sido validada por la DIAN");
        }

        BillingDocumentContext context = contextOf(note);
        CreditNoteProvider provider = resolveProvider(context);
        return context == null || !"alegra".equalsIgnoreCase(provider.getProviderName())
                ? provider.downloadCreditNotePDF(note.getCreditNoteNumber(), storeId)
                : provider.downloadCreditNotePDF(note.getCreditNoteNumber(), note.getProviderDocumentId(), storeId, context);
    }

    @Override
    public byte[] downloadCreditNoteXML(Long id) {
        Long storeId = requireStoreId();
        ElectronicCreditNote note = repository.findById(id)
                .filter(n -> storeId.equals(n.getStoreId()))
                .orElseThrow(() -> new IllegalArgumentException("Nota crédito no encontrada con ID: " + id));

        if (!note.isValidated() || note.getCreditNoteNumber() == null) {
            throw new IllegalStateException("La nota crédito no ha sido validada por la DIAN");
        }

        BillingDocumentContext context = contextOf(note);
        CreditNoteProvider provider = resolveProvider(context);
        return context == null || !"alegra".equalsIgnoreCase(provider.getProviderName())
                ? provider.downloadCreditNoteXML(note.getCreditNoteNumber(), storeId)
                : provider.downloadCreditNoteXML(note.getCreditNoteNumber(), note.getProviderDocumentId(), storeId, context);
    }

    @Override
    public CreditNoteDTO createFromSale(Long saleId, String conceptCode, String observation) {
        Objects.requireNonNull(saleId, "El saleId es obligatorio");

        // 1. Verificar existencia de factura electrónica validada para la venta
        ElectronicInvoice invoice = invoiceRepository.findBySaleIdAndStoreId(saleId, requireStoreId())
                .orElseThrow(() -> new IllegalArgumentException("No existe factura electrónica registrada para la venta ID: " + saleId));

        if (!invoice.isValidated() || invoice.getLegalNumber() == null) {
            throw new IllegalStateException("Solo se puede emitir una nota crédito sobre una factura electrónica en estado VALIDATED. Estado actual: " + invoice.getStatus());
        }

        // 2. Obtener datos de la venta
        SaleDto sale = saleFacade.getById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada con ID: " + saleId));

        // 3. Construir solicitud de nota crédito
        String chosenConcept = (conceptCode != null && !conceptCode.isBlank()) ? conceptCode.trim() : "2"; // 2 = Anulación de factura
        // Determinista por (venta, concepto): un doble clic o un reintento reutiliza la misma nota en vez de
        // emitir otra ante la DIAN (antes llevaba System.currentTimeMillis() y cada llamada creaba una nueva).
        String referenceCode = "NC-" + (sale.invoiceNumber() != null ? sale.invoiceNumber() : ("SALE-" + saleId)) + "-C" + chosenConcept;
        String obs = (observation != null && !observation.isBlank())
                ? observation.trim()
                : "Nota crédito sobre factura " + invoice.getLegalNumber();

        // Mapear payment_details
        String paymentMethod = sale.paymentMethod() != null ? sale.paymentMethod().trim().toUpperCase() : "CASH";
        String paymentMethodCode = switch (paymentMethod) {
            case "TRANSFER" -> "31";
            case "NEQUI", "DAVIPLATA" -> "42";
            case "CARD" -> "48";
            default -> "10";
        };

        BigDecimal total = sale.totalAmount() != null ? sale.totalAmount() : BigDecimal.ZERO;
        List<CreateCreditNoteRequest.PaymentDetailRequest> payments = List.of(
                new CreateCreditNoteRequest.PaymentDetailRequest("1", paymentMethodCode, "pago-" + saleId, String.format(Locale.US, "%.2f", total))
        );

        // Mapear items
        List<CreateCreditNoteRequest.ItemCreditNoteRequest> items = new ArrayList<>();
        if (sale.items() != null) {
            for (var item : sale.items()) {
                String cleanName = item.productName() != null ? item.productName().replaceAll("[^a-zA-Z0-9]", "") : "PROD";
                String productCode = "REF-" + cleanName;
                BigDecimal taxRate = item.taxRate() != null ? item.taxRate() : BigDecimal.ZERO;
                BigDecimal taxFactor = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                BigDecimal netUnitPrice = item.unitPrice().divide(taxFactor, 2, RoundingMode.HALF_UP);

                var taxItem = new CreateCreditNoteRequest.ItemCreditNoteRequest.TaxItemRequest("01", String.format(Locale.US, "%.2f", taxRate));

                items.add(new CreateCreditNoteRequest.ItemCreditNoteRequest(
                        productCode,
                        item.productName(),
                        String.format(Locale.US, "%.2f", (double) item.quantity()),
                        "0.00",
                        String.format(Locale.US, "%.2f", netUnitPrice),
                        // Servicios: los códigos con que se facturó la línea (la nota debe coincidir con la factura).
                        item.unitMeasureCode() != null && !item.unitMeasureCode().isBlank() ? item.unitMeasureCode().trim() : "94",
                        item.unspscCode() != null && !item.unspscCode().isBlank() ? item.unspscCode().trim() : "999",
                        List.of(taxItem)
                ));
            }
        }

        CreateCreditNoteRequest request = new CreateCreditNoteRequest(
                referenceCode,
                chosenConcept,
                "20",
                invoice.getLegalNumber(),
                null,
                obs,
                payments,
                null, // el proveedor tomará automáticamente los datos del cliente de la factura asociada
                items
        );

        return emit(request, saleId, canonicalMapper != null ? canonicalMapper.toInvoiceItems(sale.items()) : null);
    }

    private Long requireStoreId() {
        Long storeId = TenantContext.getStoreId();
        Preconditions.checkState(storeId != null, "La operación de nota crédito requiere un local asociado a la sesión");
        return storeId;
    }

    private BillingDocumentContext contextOf(ElectronicCreditNote note) {
        if (note.getProviderName() != null) return note.documentContext();
        if (!"alegra".equalsIgnoreCase(activeProviderName)) return null;
        throw new IllegalStateException("La nota histórica no tiene un contexto fiscal verificable; requiere revisión manual");
    }

    @Override
    public CreditNoteDTO retry(Long id) {
        Long storeId = requireStoreId();
        var reservation = emissionTransactions.reserveRetry(id, storeId);
        if (reservation.outcome() != ElectronicInvoice.ReserveOutcome.RESERVED) return reservation.note();
        var provider = resolveProvider(reservation.context());
        CreditNoteResult result;
        try {
            if (reservation.context() == null) throw new IllegalStateException("La nota no tiene un contexto fiscal verificable");
            result = provider.retryCreditNote(reservation.note().referenceCode(), reservation.context());
        } catch (Exception ex) {
            result = CreditNoteResult.pending("No se pudo recuperar la nota: " + ex.getMessage());
        }
        return emissionTransactions.complete(reservation.note().referenceCode(), storeId, result);
    }

    private CreditNoteProvider resolveProvider() {
        return resolveProvider(null);
    }

    private CreditNoteProvider resolveProvider(BillingDocumentContext context) {
        String key = context != null ? context.providerName() : ((activeProviderName != null) ? activeProviderName.trim().toLowerCase() : "alegra");
        CreditNoteProvider provider = providersMap.get(key);
        if (provider == null) {
            // Sin respaldo silencioso a 'mock': un BILLING_PROVIDER mal escrito emitiria facturas falsas.
            throw new IllegalStateException("Proveedor de facturación '" + key + "' no registrado (billing.provider). Disponibles: " + providersMap.keySet() + ". " + "No hay ningún proveedor de notas crédito configurado o disponible.");
        }
        return provider;
    }

}
