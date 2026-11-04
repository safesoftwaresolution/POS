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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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
    private final InvoiceEmissionTransactions emissions;
    private final Map<String, InvoiceProvider> providersMap;

    @Value("${billing.provider:factus}")
    private String activeProviderName;

    public InvoiceServiceImpl(
            ElectronicInvoiceRepository repository,
            BillingCanonicalMapper canonicalMapper,
            ElectronicInvoiceMapper invoiceMapper,
            @Lazy SaleFacade saleFacade,
            InvoiceEmissionTransactions emissions,
            List<InvoiceProvider> providers
    ) {
        this.repository = repository;
        this.canonicalMapper = canonicalMapper;
        this.invoiceMapper = invoiceMapper;
        this.saleFacade = saleFacade;
        this.emissions = emissions;
        this.providersMap = providers.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderName().toLowerCase(),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    /**
     * Emite la factura de la venta en tres pasos: reservar (transacción corta con lock), llamar al proveedor SIN
     * transacción y registrar el resultado (transacción corta). Así ninguna llamada de red retiene una conexión
     * ni un lock de base de datos, y dos emisiones simultáneas de la misma venta llaman al proveedor una sola vez.
     */
    @Override
    public ElectronicInvoiceDto processElectronicInvoice(SaleDto sale) {
        Objects.requireNonNull(sale, "El objeto SaleDto no puede ser nulo");
        if (sale.id() == null) {
            throw new IllegalArgumentException("El ID de la venta es obligatorio para facturar");
        }
        // Sin local no se factura: antes se asumía el local 1 y se podía emitir con el equivocado.
        Long effectiveStoreId = sale.storeId() != null ? sale.storeId() : TenantContext.getStoreId();
        if (effectiveStoreId == null) {
            throw new IllegalStateException("No se puede facturar la venta " + sale.id() + " sin un local asociado");
        }

        // Paso A: reservar.
        InvoiceEmissionTransactions.Reservation reservation = reserve(sale.id(), effectiveStoreId);
        if (reservation.outcome() == ElectronicInvoice.ReserveOutcome.ALREADY_VALIDATED) {
            log.info("La venta ID {} ya posee una factura electrónica VALIDATED ({})", sale.id(),
                    reservation.invoice().factusNumber());
            return reservation.invoice();
        }
        if (reservation.outcome() == ElectronicInvoice.ReserveOutcome.IN_PROGRESS) {
            log.info("La venta ID {} ya tiene una emisión en curso; no se emite de nuevo", sale.id());
            return reservation.invoice();
        }

        // Paso B: llamada al proveedor, sin transacción. Cualquier fallo se convierte en un resultado de error.
        InvoiceResult result = null;
        InvoiceProvider provider = null;
        try {
            provider = resolveProvider();
            if (reservation.reconcileProviderDocumentId() != null) {
                result = reconcile(provider, reservation.reconcileProviderDocumentId());
            }
            if (result == null) {
                InvoiceRequest request = canonicalMapper.toInvoiceRequest(sale);
                result = provider.emitInvoice(request);
            }
        } catch (Exception ex) {
            String providerName = provider != null ? provider.getProviderName() : activeProviderName;
            log.error("Error al ejecutar emision con proveedor {}: {}", providerName, ex.getMessage());
            result = InvoiceResult.error("Error en proveedor " + providerName + ": " + ex.getMessage());
        }

        // Paso C: registrar el resultado.
        return emissions.complete(sale.id(), result);
    }

    /**
     * El proveedor ya recibió este documento: se pregunta por su estado en lugar de emitirlo de nuevo (nunca se crea
     * un duplicado ante la DIAN). Devuelve null si el proveedor no sabe conciliar, y entonces se emite normalmente.
     * Un fallo al consultar deja la factura PENDING: ante la duda no se emite.
     */
    private InvoiceResult reconcile(InvoiceProvider provider, String providerDocumentId) {
        try {
            InvoiceResult queried = provider.queryInvoiceByProviderId(providerDocumentId);
            if (queried == null || "ERROR".equalsIgnoreCase(queried.status())) {
                String reason = queried != null ? queried.errorMessage() : "respuesta vacía";
                return InvoiceResult.pending("No se pudo conciliar la factura con el proveedor: " + reason)
                        .withProviderDocumentId(providerDocumentId);
            }
            return queried;
        } catch (UnsupportedOperationException cannotReconcile) {
            return null;
        } catch (Exception ex) {
            log.warn("No se pudo conciliar el documento {} con el proveedor: {}", providerDocumentId, ex.getMessage());
            return InvoiceResult.pending("No se pudo conciliar la factura con el proveedor: " + ex.getMessage())
                    .withProviderDocumentId(providerDocumentId);
        }
    }

    /** Dos hilos creando a la vez la fila de una venta nueva: el perdedor reintenta y ya la encuentra. */
    private InvoiceEmissionTransactions.Reservation reserve(Long saleId, Long storeId) {
        try {
            return emissions.reserve(saleId, storeId);
        } catch (DataIntegrityViolationException raceOnFirstInsert) {
            return emissions.reserve(saleId, storeId);
        }
    }

    // No es readOnly: si la venta aún no tiene registro de factura, se crea en PENDING. Antes la
    // lectura era readOnly y el INSERT interno (REQUIRES_NEW por auto-invocación, que nunca aplicó)
    // podía fallar en PostgreSQL con "cannot execute INSERT in a read-only transaction".
    @Override
    @Transactional
    public Optional<ElectronicInvoiceDto> getBySaleId(Long saleId) {
        Long storeId = TenantContext.getStoreId();
        if (saleId == null || storeId == null) {
            return Optional.empty();
        }
        if (saleFacade.getById(saleId).isEmpty()) {
            return Optional.empty();
        }
        ElectronicInvoice einvoice = getOrCreateInitialInvoice(saleId, storeId);
        return Optional.ofNullable(invoiceMapper.toDto(einvoice));
    }

    /** El reintento manual reinicia la factura en una transacción corta y emite fuera de cualquier transacción. */
    @Override
    public ElectronicInvoiceDto retryInvoice(Long saleId) {
        if (saleId == null) {
            throw new IllegalArgumentException("El ID de la venta es obligatorio");
        }

        SaleDto sale = saleFacade.getById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada con ID: " + saleId));

        emissions.resetForRetry(saleId, requireStoreId());
        return processElectronicInvoice(sale);
    }

    @Override
    @Transactional(readOnly = true)
    public void sendInvoiceEmail(Long saleId, String email) {
        Objects.requireNonNull(saleId, "El ID de la venta es obligatorio");
        Objects.requireNonNull(email, "El correo electrónico es obligatorio");
        assertSaleVisible(saleId);

        ElectronicInvoice einvoice = repository.findBySaleIdAndStoreId(saleId, requireStoreId())
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
        Optional<Long> sellerId = saleFacade.restrictedSellerId();
        if (sellerId.isPresent()) {
            // Un vendedor solo ve las facturas de sus ventas (RN-SALE-005); el orden es fijo.
            var unsorted = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
            if (status != null && !status.isBlank()) {
                try {
                    String statusName = ElectronicInvoiceStatus.valueOf(status.trim().toUpperCase()).name();
                    return repository.findByStoreIdAndStatusAndSeller(storeId, statusName, sellerId.get(), unsorted).map(invoiceMapper::toDto);
                } catch (IllegalArgumentException ex) {
                    return org.springframework.data.domain.Page.empty();
                }
            }
            return repository.findByStoreIdAndSeller(storeId, sellerId.get(), unsorted).map(invoiceMapper::toDto);
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
        assertSaleVisible(saleId);
        ElectronicInvoice einvoice = repository.findBySaleIdAndStoreId(saleId, requireStoreId())
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada para la venta: " + saleId));

        einvoice.assertCanDownloadLegalDocument();

        InvoiceProvider provider = resolveProvider();
        return provider.downloadInvoicePDF(einvoice.getFactusNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadInvoiceXML(Long saleId) {
        assertSaleVisible(saleId);
        ElectronicInvoice einvoice = repository.findBySaleIdAndStoreId(saleId, requireStoreId())
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada para la venta: " + saleId));

        einvoice.assertCanDownloadLegalDocument();

        InvoiceProvider provider = resolveProvider();
        return provider.downloadInvoiceXML(einvoice.getFactusNumber());
    }

    /** Un vendedor no puede operar la factura de una venta ajena: responde como si no existiera. */
    private void assertSaleVisible(Long saleId) {
        if (saleFacade.restrictedSellerId().isPresent() && saleFacade.getById(saleId).isEmpty()) {
            throw new IllegalArgumentException("Factura no encontrada para la venta: " + saleId);
        }
    }

    private ElectronicInvoice getOrCreateInitialInvoice(Long saleId, Long storeId) {
        return repository.findBySaleIdAndStoreId(saleId, storeId)
                .orElseGet(() -> repository.save(ElectronicInvoice.initialize(saleId, storeId)));
    }

    private Long requireStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("La operación de factura electrónica requiere un local asociado a la sesión");
        }
        return storeId;
    }

    private InvoiceProvider resolveProvider() {
        String key = (activeProviderName != null) ? activeProviderName.trim().toLowerCase() : "factus";
        InvoiceProvider provider = providersMap.get(key);
        if (provider == null) {
            // Sin respaldo silencioso a 'mock': un BILLING_PROVIDER mal escrito emitiria facturas falsas.
            throw new IllegalStateException("Proveedor de facturación '" + key + "' no registrado (billing.provider). Disponibles: " + providersMap.keySet() + ". " + "No hay ningún proveedor de facturación configurado o disponible.");
        }
        return provider;
    }
}
