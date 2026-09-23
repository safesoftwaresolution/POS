package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceDto;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementación del servicio de Facturación Electrónica de Ventas.
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

        // 1. Obtener (con bloqueo pesimista) o crear el registro de factura de esta venta.
        Long effectiveStoreId = sale.storeId() != null ? sale.storeId() : TenantContext.getStoreId();
        ElectronicInvoice einvoice = lockOrCreateInvoice(sale.id(), effectiveStoreId);
        if (einvoice.getStoreId() == null && effectiveStoreId != null) {
            einvoice.setStoreId(effectiveStoreId);
        }

        // 2. Guard de idempotencia bajo bloqueo: si ya está validada, no volver a emitir.
        if ("VALIDATED".equalsIgnoreCase(einvoice.getStatus())) {
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

        // 6. Aplicar resultados normalizados y persistir en la misma transacción.
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
        if (existingOpt.isPresent() && "VALIDATED".equalsIgnoreCase(existingOpt.get().getStatus())) {
            throw new IllegalArgumentException("La factura ya está validada y no puede reintentarse");
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

        if (!"VALIDATED".equalsIgnoreCase(einvoice.getStatus()) || einvoice.getFactusNumber() == null || einvoice.getFactusNumber().isBlank()) {
            throw new IllegalStateException("La factura no ha sido validada por la DIAN (estado actual: " + einvoice.getStatus() + ")");
        }

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
            page = repository.findByStoreIdAndStatus(storeId, status.trim().toUpperCase(), pageable);
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

        if (einvoice.getFactusNumber() == null || einvoice.getFactusNumber().isBlank()) {
            throw new IllegalStateException("La factura no tiene número legal asignado");
        }

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

        if (einvoice.getFactusNumber() == null || einvoice.getFactusNumber().isBlank()) {
            throw new IllegalStateException("La factura no tiene número legal asignado");
        }

        InvoiceProvider provider = resolveProvider();
        return provider.downloadInvoiceXML(einvoice.getFactusNumber());
    }

    private ElectronicInvoice lockOrCreateInvoice(Long saleId, Long storeId) {
        return repository.findBySaleIdForUpdate(saleId)
                .orElseGet(() -> {
                    ElectronicInvoice newInv = new ElectronicInvoice();
                    newInv.setSaleId(saleId);
                    newInv.setStoreId(storeId != null ? storeId : 1L);
                    newInv.setStatus("PENDING");
                    return repository.saveAndFlush(newInv);
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ElectronicInvoice getOrCreateInitialInvoice(Long saleId) {
        return repository.findBySaleId(saleId)
                .orElseGet(() -> {
                    ElectronicInvoice newInv = new ElectronicInvoice();
                    newInv.setSaleId(saleId);
                    Long storeId = saleFacade.getById(saleId).map(s -> s.storeId()).orElse(TenantContext.getStoreId());
                    newInv.setStoreId(storeId != null ? storeId : 1L);
                    newInv.setStatus("PENDING");
                    return repository.save(newInv);
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
        if (result != null) {
            if (result.legalInvoiceNumber() != null) {
                einvoice.setFactusNumber(result.legalInvoiceNumber());
            }
            if (result.cufe() != null) {
                einvoice.setCufe(result.cufe());
            }
            if (result.qrCode() != null) {
                einvoice.setQrCode(result.qrCode());
            }
            if (result.pdfUrl() != null) {
                einvoice.setPdfUrl(result.pdfUrl());
            }
            if (result.status() != null) {
                einvoice.setStatus(result.status());
            }
            einvoice.setErrorMessage(result.errorMessage());
            if ("VALIDATED".equalsIgnoreCase(result.status())) {
                einvoice.setValidatedAt(result.validatedAt() != null ? result.validatedAt() : LocalDateTime.now());
            }
        }
    }
}
