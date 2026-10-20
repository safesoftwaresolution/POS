package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceDto;
import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import com.sciencebot.pos.billing.internal.adapters.InvoiceProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.mappers.BillingCanonicalMapper;
import com.sciencebot.pos.billing.internal.mappers.ElectronicInvoiceMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementación del servicio de Facturación Electrónica de Ventas (Vertical Slice).
 * Orquesta la integración técnica delegando invariantes y transiciones a la entidad ElectronicInvoice.
 */
@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);

    private final ElectronicInvoiceRepository repository;
    private final BillingCanonicalMapper canonicalMapper;
    private final ElectronicInvoiceMapper invoiceMapper;
    private final SaleFacade saleFacade;
    private final InvoiceService self;
    private final Map<String, InvoiceProvider> providersMap;

    @Value("${billing.provider:factus}")
    private String activeProviderName;

    public InvoiceServiceImpl(
            ElectronicInvoiceRepository repository,
            BillingCanonicalMapper canonicalMapper,
            ElectronicInvoiceMapper invoiceMapper,
            @Lazy SaleFacade saleFacade,
            @Lazy InvoiceService self,
            List<InvoiceProvider> providers
    ) {
        this.repository = repository;
        this.canonicalMapper = canonicalMapper;
        this.invoiceMapper = invoiceMapper;
        this.saleFacade = saleFacade;
        this.self = self;
        this.providersMap = providers.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderName().toLowerCase(),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    @Override
    @Transactional
    public ElectronicInvoiceDto processElectronicInvoice(SaleDto sale) {
        Objects.requireNonNull(sale, "El objeto SaleDto no puede ser nulo");
        if (sale.id() == null) {
            throw new IllegalArgumentException("El ID de la venta es obligatorio para facturar");
        }

        // 1. Obtener (con bloqueo pesimista) o inicializar el registro de factura para esta venta.
        Long effectiveStoreId = sale.storeId() != null ? sale.storeId() : TenantContext.getStoreId();
        ElectronicInvoice einvoice = lockOrCreateInvoice(sale.id(), effectiveStoreId);
        einvoice.assignStoreIdIfMissing(effectiveStoreId);

        // 2. Guard de idempotencia delegado al dominio: si ya está validada, no volver a emitir.
        if (einvoice.isValidated()) {
            log.info("La venta ID {} ya posee una factura electrónica VALIDATED ({})", sale.id(), einvoice.getFactusNumber());
            return invoiceMapper.toDto(einvoice);
        }

        // 3. Resolver la estrategia activa de facturación
        InvoiceProvider provider = resolveProvider();

        // 4. Mapear a solicitud canónica agnóstica
        InvoiceRequest request = canonicalMapper.toInvoiceRequest(sale);

        // 5. Ejecutar la llamada I/O al proveedor
        InvoiceResult result;
        try {
            result = provider.emitInvoice(request);
        } catch (Exception ex) {
            log.error("Error al ejecutar emision con proveedor {}: {}", provider.getProviderName(), ex.getMessage());
            result = InvoiceResult.error("Error en proveedor " + provider.getProviderName() + ": " + ex.getMessage());
        }

        // 6. Aplicar resultados normalizados delegando al dominio y persistir.
        applyResultToEntity(einvoice, result);
        ElectronicInvoice saved = repository.save(einvoice);
        return invoiceMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ElectronicInvoiceDto> getBySaleId(Long saleId) {
        if (saleId == null) {
            return Optional.empty();
        }
        if (saleFacade.getById(saleId).isEmpty()) {
            return Optional.empty();
        }
        ElectronicInvoice einvoice = getOrCreateInitialInvoice(saleId);
        return Optional.ofNullable(invoiceMapper.toDto(einvoice));
    }

    @Override
    public ElectronicInvoiceDto retryInvoice(Long saleId) {
        if (saleId == null) {
            throw new IllegalArgumentException("El ID de la venta es obligatorio");
        }

        SaleDto sale = saleFacade.getById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada con ID: " + saleId));

        Optional<ElectronicInvoice> existingOpt = repository.findBySaleId(saleId);
        if (existingOpt.isPresent()) {
            existingOpt.get().prepareForRetry(); // La entidad valida invariante y estado
        }

        return (self != null ? self : this).processElectronicInvoice(sale);
    }

    @Override
    @Transactional(readOnly = true)
    public void sendInvoiceEmail(Long saleId, String email) {
        Objects.requireNonNull(saleId, "El ID de la venta es obligatorio");
        Objects.requireNonNull(email, "El correo electrónico es obligatorio");

        ElectronicInvoice einvoice = repository.findBySaleId(saleId)
                .orElseThrow(() -> new IllegalArgumentException("No existe factura electrónica registrada para la venta ID: " + saleId));

        einvoice.assertCanSendEmail(); // Delegación de invariante a la entidad

        InvoiceProvider provider = resolveProvider();
        boolean sent = provider.sendInvoiceEmail(einvoice.getFactusNumber(), email);
        if (!sent) {
            throw new IllegalStateException("El proveedor no pudo procesar el envío de correo para la factura " + einvoice.getFactusNumber());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ElectronicInvoiceDto> listInvoices(org.springframework.data.domain.Pageable pageable, String status) {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            return org.springframework.data.domain.Page.empty();
        }
        org.springframework.data.domain.Page<ElectronicInvoice> page;
        if (status != null && !status.isBlank()) {
            try {
                ElectronicInvoiceStatus statusEnum = ElectronicInvoiceStatus.valueOf(status.trim().toUpperCase());
                page = repository.findByStoreIdAndStatus(storeId, statusEnum, pageable);
            } catch (IllegalArgumentException ex) {
                return org.springframework.data.domain.Page.empty();
            }
        } else {
            page = repository.findByStoreId(storeId, pageable);
        }
        return page.map(invoiceMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadInvoicePDF(Long saleId) {
        Long storeId = TenantContext.getStoreId();
        ElectronicInvoice einvoice = repository.findBySaleId(saleId)
                .filter(inv -> storeId == null || storeId.equals(inv.getStoreId()))
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada para la venta: " + saleId));

        einvoice.assertCanDownloadLegalDocument();

        InvoiceProvider provider = resolveProvider();
        return provider.downloadInvoicePDF(einvoice.getFactusNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadInvoiceXML(Long saleId) {
        Long storeId = TenantContext.getStoreId();
        ElectronicInvoice einvoice = repository.findBySaleId(saleId)
                .filter(inv -> storeId == null || storeId.equals(inv.getStoreId()))
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada para la venta: " + saleId));

        einvoice.assertCanDownloadLegalDocument();

        InvoiceProvider provider = resolveProvider();
        return provider.downloadInvoiceXML(einvoice.getFactusNumber());
    }

    private ElectronicInvoice lockOrCreateInvoice(Long saleId, Long storeId) {
        return repository.findBySaleIdForUpdate(saleId)
                .orElseGet(() -> repository.saveAndFlush(ElectronicInvoice.initialize(saleId, storeId != null ? storeId : 1L)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ElectronicInvoice getOrCreateInitialInvoice(Long saleId) {
        return repository.findBySaleId(saleId)
                .orElseGet(() -> {
                    Long storeId = saleFacade.getById(saleId).map(SaleDto::storeId).orElse(TenantContext.getStoreId());
                    return repository.save(ElectronicInvoice.initialize(saleId, storeId != null ? storeId : 1L));
                });
    }

    private InvoiceProvider resolveProvider() {
        String key = (activeProviderName != null) ? activeProviderName.trim().toLowerCase() : "factus";
        InvoiceProvider provider = providersMap.get(key);
        if (provider == null) {
            log.warn("Proveedor de facturación '{}' no encontrado. Utilizando proveedor por defecto 'mock'", key);
            provider = providersMap.get("mock");
        }
        if (provider == null) {
            throw new IllegalStateException("No hay ningún proveedor de facturación configurado o disponible.");
        }
        return provider;
    }

    private void applyResultToEntity(ElectronicInvoice einvoice, InvoiceResult result) {
        if (result == null) {
            einvoice.markAsError("Respuesta vacía del proveedor de facturación");
            return;
        }

        if ("VALIDATED".equalsIgnoreCase(result.status())) {
            einvoice.markAsValidated(
                    result.legalInvoiceNumber(),
                    result.cufe(),
                    result.qrCode(),
                    result.pdfUrl(),
                    result.validatedAt()
            );
        } else if ("REJECTED".equalsIgnoreCase(result.status())) {
            einvoice.markAsRejected(result.errorMessage());
        } else {
            einvoice.markAsError(result.errorMessage());
        }
    }
}
