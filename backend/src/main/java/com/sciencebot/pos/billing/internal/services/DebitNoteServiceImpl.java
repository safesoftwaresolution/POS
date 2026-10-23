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
    private final Map<String, DebitNoteProvider> providersMap;

    @Value("${billing.provider:factus}")
    private String activeProviderName;

    public DebitNoteServiceImpl(
            ElectronicDebitNoteRepository repository,
            ElectronicDebitNoteMapper mapper,
            ElectronicInvoiceRepository invoiceRepository,
            List<DebitNoteProvider> providers
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.invoiceRepository = invoiceRepository;
        this.providersMap = providers.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderName().toLowerCase(),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    @Override
    @Transactional
    public DebitNoteDTO createAndValidateDebitNote(CreateDebitNoteRequest request) {
        return emit(request, null);
    }

    @Override
    @Transactional
    public DebitNoteDTO createForSale(Long saleId, CreateDebitNoteRequest request) {
        Preconditions.requireNotNull(saleId, "El saleId es obligatorio");
        Preconditions.requireNotNull(request, "La solicitud de nota débito no puede ser nula");
        Long storeId = requireStoreId();

        ElectronicInvoice invoice = invoiceRepository.findBySaleIdAndStoreId(saleId, storeId)
                .orElseThrow(() -> new EntityNotFoundException("No existe factura electrónica registrada para la venta ID: " + saleId));
        Preconditions.checkState(invoice.isValidated() && invoice.getFactusNumber() != null,
                "Solo se puede emitir una nota débito sobre una factura electrónica en estado VALIDATED. Estado actual: " + invoice.getStatus());

        String billNumber = invoice.getFactusNumber();
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
                request.customer(), // null = Factus toma los datos del cliente de la factura referenciada
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

    @Override
    @Transactional
    public boolean deleteUnvalidatedDebitNote(String referenceCode) {
        String reference = Preconditions.requireNotBlank(referenceCode, "El reference_code es obligatorio");
        Long storeId = requireStoreId();

        Optional<ElectronicDebitNote> noteOpt = repository.findByReferenceCodeAndStoreId(reference, storeId);
        noteOpt.ifPresent(n -> Preconditions.checkState(!n.isValidated(),
                "Una nota débito VALIDATED por la DIAN no puede eliminarse"));

        boolean deleted = resolveProvider().deleteUnvalidatedDebitNote(reference, storeId);
        if (deleted) {
            noteOpt.ifPresent(repository::delete);
        }
        return deleted;
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

        // 1. Guard de idempotencia bajo bloqueo pesimista sobre la referencia
        Optional<ElectronicDebitNote> existingOpt = repository.findByReferenceCodeForUpdate(referenceCode);
        ElectronicDebitNote note;
        if (existingOpt.isPresent()) {
            note = existingOpt.get();
            // reference_code es UNIQUE global: nunca se reutiliza ni se expone la nota de otro local
            Preconditions.checkState(storeId.equals(note.getStoreId()),
                    "El reference_code " + referenceCode + " ya está en uso");
            Preconditions.checkState(billNumber.equals(note.getBillNumber()),
                    "El reference_code " + referenceCode + " ya fue usado para la factura " + note.getBillNumber());
            if (note.isValidated()) {
                log.info("La nota débito con referencia {} ya se encuentra VALIDATED en el sistema ({})",
                        referenceCode, note.getDebitNoteNumber());
                return mapper.toDto(note);
            }
            note.prepareForResend();
        } else {
            note = repository.saveAndFlush(ElectronicDebitNote.initialize(
                    storeId,
                    saleId,
                    referenceCode,
                    billNumber,
                    request.correctionConceptCode(),
                    request.customizationId()
            ));
        }

        // 2. Resolver estrategia de proveedor activa
        DebitNoteProvider provider = resolveProvider();

        // 3. Ejecutar emisión hacia el proveedor
        DebitNoteResult result;
        try {
            result = provider.emitDebitNote(request, storeId);

            // Recuperación automática ante conflicto 409 (documento pendiente no validado en Factus)
            if (!result.isSuccess() && result.errorMessage() != null && result.errorMessage().contains("409")) {
                log.warn("Detectado conflicto 409 en Factus para nota débito {}. Intentando eliminación previa y reintento...", referenceCode);
                boolean deleted = provider.deleteUnvalidatedDebitNote(referenceCode, storeId);
                if (deleted) {
                    result = provider.emitDebitNote(request, storeId);
                }
            }
        } catch (Exception ex) {
            log.error("Error al procesar emisión de nota débito con proveedor {}: {}", provider.getProviderName(), ex.getMessage(), ex);
            result = DebitNoteResult.error("Error en proveedor " + provider.getProviderName() + ": " + ex.getMessage());
        }

        // 4. Aplicar resultados a la entidad y persistir
        applyResultToEntity(note, result);
        ElectronicDebitNote saved = repository.save(note);
        return mapper.toDto(saved);
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
        String key = (activeProviderName != null) ? activeProviderName.trim().toLowerCase() : "factus";
        DebitNoteProvider provider = providersMap.get(key);
        if (provider == null) {
            // Sin respaldo silencioso a 'mock': un BILLING_PROVIDER mal escrito emitiria facturas falsas.
            throw new IllegalStateException("Proveedor de facturación '" + key + "' no registrado (billing.provider). Disponibles: " + providersMap.keySet() + ". " + "No hay ningún proveedor de notas débito configurado o disponible.");
        }
        return provider;
    }

    private void applyResultToEntity(ElectronicDebitNote note, DebitNoteResult result) {
        if (result == null) {
            note.markAsError("Respuesta vacía del proveedor de facturación");
            return;
        }

        if ("VALIDATED".equalsIgnoreCase(result.status())) {
            if (result.debitNoteNumber() == null || result.debitNoteNumber().isBlank()) {
                note.markAsError("El proveedor reportó la nota débito como validada pero no retornó su número legal");
                return;
            }
            note.markAsValidated(
                    result.debitNoteNumber(),
                    result.cude(),
                    result.qrCode(),
                    result.pdfUrl(),
                    result.validatedAt()
            );
        } else if ("REJECTED".equalsIgnoreCase(result.status())) {
            note.markAsRejected(Preconditions.defaultIfBlank(result.errorMessage(), "Nota débito rechazada por la DIAN"));
        } else {
            note.markAsError(Preconditions.defaultIfBlank(result.errorMessage(), "Error en proceso de nota débito"));
        }
    }
}
