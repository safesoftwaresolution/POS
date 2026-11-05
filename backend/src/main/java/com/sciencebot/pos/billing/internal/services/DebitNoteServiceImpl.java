package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.dto.DebitNoteDTO;
import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import com.sciencebot.pos.billing.internal.adapters.DebitNoteProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.DebitNoteResult;
import com.sciencebot.pos.billing.internal.entities.ElectronicDebitNote;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.mappers.ElectronicDebitNoteMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicDebitNoteRepository;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.shared.validation.Preconditions;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementación del servicio de Notas Débito electrónicas (Vertical Slice).
 * Orquesta la emisión ante el proveedor activo y delega invariantes y transiciones a {@link ElectronicDebitNote}.
 * Toda operación exige el local de la solicitud en curso: nunca se emite ni se consulta con el de otro local.
 */
@Service
public class DebitNoteServiceImpl implements DebitNoteService {

    private static final Logger log = LoggerFactory.getLogger(DebitNoteServiceImpl.class);

    /** Nota débito con referencia a una factura electrónica (único tipo soportado: la tabla exige bill_number). */
    private static final String CUSTOMIZATION_WITH_INVOICE_REFERENCE = "30";

    private final ElectronicDebitNoteRepository repository;
    private final ElectronicDebitNoteMapper mapper;
    private final ElectronicInvoiceRepository invoiceRepository;
    private final DebitNoteEmissionTransactions emissionTransactions;
    private final Map<String, DebitNoteProvider> providersMap;

    @Value("${billing.provider:alegra}")
    private String activeProviderName;

    public DebitNoteServiceImpl(
            ElectronicDebitNoteRepository repository,
            ElectronicDebitNoteMapper mapper,
            ElectronicInvoiceRepository invoiceRepository,
            DebitNoteEmissionTransactions emissionTransactions,
            List<DebitNoteProvider> providers
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.invoiceRepository = invoiceRepository;
        this.emissionTransactions = emissionTransactions;
        this.providersMap = providers.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderName().toLowerCase(),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    @Override
    public DebitNoteDTO createAndValidateDebitNote(CreateDebitNoteRequest request) {
        return emit(request, null);
    }

    @Override
    public DebitNoteDTO createForSale(Long saleId, CreateDebitNoteRequest request) {
        Preconditions.requireNotNull(saleId, "El saleId es obligatorio");
        Preconditions.requireNotNull(request, "La solicitud de nota débito no puede ser nula");
        Long storeId = requireStoreId();

        ElectronicInvoice invoice = invoiceRepository.findBySaleIdAndStoreId(saleId, storeId)
                .orElseThrow(() -> new EntityNotFoundException("No existe factura electrónica registrada para la venta ID: " + saleId));
        Preconditions.checkState(invoice.isValidated() && invoice.getLegalNumber() != null,
                "Solo se puede emitir una nota débito sobre una factura electrónica en estado VALIDATED. Estado actual: " + invoice.getStatus());

        String billNumber = invoice.getLegalNumber();
        if (request.billNumber() != null && !request.billNumber().isBlank()) {
            Preconditions.checkArgument(billNumber.equals(request.billNumber().trim()),
                    "El bill_number " + request.billNumber().trim() + " no corresponde a la factura de la venta (" + billNumber + ")");
        }

        CreateDebitNoteRequest boundRequest = new CreateDebitNoteRequest(
                request.referenceCode(),
                request.correctionConceptCode(),
                CUSTOMIZATION_WITH_INVOICE_REFERENCE,
                billNumber,
                request.numberingRangeId(),
                request.observation(),
                request.paymentDetails(),
                request.customer(), // null = el proveedor toma los datos del cliente de la factura referenciada
                request.items()
        );
        return emit(boundRequest, saleId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DebitNoteDTO> getById(Long id) {
        Long storeId = TenantContext.getStoreId();
        if (id == null || storeId == null) return Optional.empty();
        return repository.findById(id)
                .filter(n -> storeId.equals(n.getStoreId()))
                .map(mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DebitNoteDTO> getByReferenceCode(String referenceCode) {
        Long storeId = TenantContext.getStoreId();
        if (referenceCode == null || referenceCode.isBlank() || storeId == null) return Optional.empty();
        return repository.findByReferenceCodeAndStoreId(referenceCode.trim(), storeId)
                .map(mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DebitNoteDTO> listDebitNotes(Pageable pageable, String status) {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            return Page.empty();
        }

        Page<ElectronicDebitNote> page;
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
    @Transactional(readOnly = true)
    public byte[] downloadDebitNotePDF(Long id) {
        Long storeId = requireStoreId();
        ElectronicDebitNote note = findValidatedNote(id, storeId);
        return resolveProvider().downloadDebitNotePDF(note.getDebitNoteNumber(), storeId);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadDebitNoteXML(Long id) {
        Long storeId = requireStoreId();
        ElectronicDebitNote note = findValidatedNote(id, storeId);
        return resolveProvider().downloadDebitNoteXML(note.getDebitNoteNumber(), storeId);
    }

    private DebitNoteDTO emit(CreateDebitNoteRequest request, Long saleId) {
        Preconditions.requireNotNull(request, "La solicitud de nota débito no puede ser nula");
        String referenceCode = Preconditions.requireNotBlank(request.referenceCode(), "El reference_code es obligatorio para la nota débito");
        Preconditions.checkArgument(CUSTOMIZATION_WITH_INVOICE_REFERENCE.equals(request.customizationId().trim()),
                "Solo se soportan notas débito con referencia a factura electrónica (customization_id=30)");
        String billNumber = Preconditions.requireNotBlank(request.billNumber(),
                "El bill_number de la factura electrónica referenciada es obligatorio");
        // Se valida aquí además del @Valid del DTO: no se debe enviar a la DIAN una nota incompleta
        Preconditions.requireNotBlank(request.correctionConceptCode(), "El correction_concept_code es obligatorio");
        Preconditions.requireNotEmpty(request.items(), "La nota débito debe contener al menos un ítem");
        Long storeId = requireStoreId();

        DebitNoteProvider provider = resolveProvider();

        // Paso A: reserva (transacción corta con lock). Si la nota ya está validada o la emite otro hilo, se
        // devuelve su estado actual.
        DebitNoteEmissionTransactions.Reservation reservation = reserve(request, saleId, storeId);
        if (reservation.outcome() != ElectronicInvoice.ReserveOutcome.RESERVED) {
            log.info("La nota débito con referencia {} no se emite ahora: {}", referenceCode, reservation.outcome());
            return reservation.note();
        }

        // Paso B: proveedor, sin transacción abierta.
        DebitNoteResult result;
        try {
            result = provider.emitDebitNote(request, storeId);
        } catch (Exception ex) {
            log.error("Error al procesar emisión de nota débito con proveedor {}: {}", provider.getProviderName(), ex.getMessage(), ex);
            result = DebitNoteResult.error("Error en proveedor " + provider.getProviderName() + ": " + ex.getMessage());
        }

        // Paso C: registrar el resultado.
        return emissionTransactions.complete(referenceCode, storeId, result);
    }

    private DebitNoteEmissionTransactions.Reservation reserve(CreateDebitNoteRequest request, Long saleId, Long storeId) {
        try {
            return emissionTransactions.reserve(request, saleId, storeId);
        } catch (DataIntegrityViolationException concurrentInsert) {
            // Dos hilos crearon la misma referencia a la vez: el segundo ya la encuentra y se encola detrás del lock.
            return emissionTransactions.reserve(request, saleId, storeId);
        }
    }

    private ElectronicDebitNote findValidatedNote(Long id, Long storeId) {
        ElectronicDebitNote note = repository.findById(Preconditions.requireNotNull(id, "El id de la nota débito es obligatorio"))
                .filter(n -> storeId.equals(n.getStoreId()))
                .orElseThrow(() -> new EntityNotFoundException("Nota débito no encontrada con ID: " + id));
        Preconditions.checkState(note.isValidated() && note.getDebitNoteNumber() != null,
                "La nota débito no ha sido validada por la DIAN");
        return note;
    }

    private Long requireStoreId() {
        Long storeId = TenantContext.getStoreId();
        Preconditions.checkState(storeId != null, "La operación de nota débito requiere un local asociado a la sesión");
        return storeId;
    }

    private DebitNoteProvider resolveProvider() {
        String key = (activeProviderName != null) ? activeProviderName.trim().toLowerCase() : "alegra";
        DebitNoteProvider provider = providersMap.get(key);
        if (provider == null) {
            // Sin respaldo silencioso a 'mock': un BILLING_PROVIDER mal escrito emitiria facturas falsas.
            throw new IllegalStateException("Proveedor de facturación '" + key + "' no registrado (billing.provider). Disponibles: " + providersMap.keySet() + ". " + "No hay ningún proveedor de notas débito configurado o disponible.");
        }
        return provider;
    }

}
