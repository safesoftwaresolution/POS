package com.sciencebot.pos.sales.internal.services;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.sales.*;
import com.sciencebot.pos.sales.internal.entities.Sale;
import com.sciencebot.pos.sales.internal.entities.SaleItem;
import com.sciencebot.pos.sales.internal.entities.SaleReturn;
import com.sciencebot.pos.sales.internal.entities.SaleReturnItem;
import com.sciencebot.pos.sales.internal.repositories.SaleItemRepository;
import com.sciencebot.pos.sales.internal.repositories.SaleRepository;
import com.sciencebot.pos.sales.internal.repositories.SaleReturnRepository;
import com.sciencebot.pos.sales.internal.mappers.SaleMapper;
import com.sciencebot.pos.sales.internal.mappers.SaleReturnMapper;
import com.sciencebot.pos.customers.CreateCustomerCommand;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.inventory.InventoryFacade;
import com.sciencebot.pos.users.UserFacade;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SaleServiceImpl implements SaleFacade {

    private static final String GENERAL_CUSTOMER_IDENTIFICATION = "9999999999";

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SaleReturnRepository saleReturnRepository;
    private final SaleMapper saleMapper;
    private final SaleReturnMapper saleReturnMapper;
    private final CustomerFacade customerFacade;
    private final ProductFacade productFacade;
    private final InventoryFacade inventoryFacade;
    private final UserFacade userFacade;
    private final com.sciencebot.pos.billing.api.BillingFacade billingFacade;

    public SaleServiceImpl(
            SaleRepository saleRepository,
            SaleItemRepository saleItemRepository,
            SaleReturnRepository saleReturnRepository,
            SaleMapper saleMapper,
            SaleReturnMapper saleReturnMapper,
            @Lazy CustomerFacade customerFacade,
            @Lazy ProductFacade productFacade,
            @Lazy InventoryFacade inventoryFacade,
            @Lazy UserFacade userFacade,
            @Lazy com.sciencebot.pos.billing.api.BillingFacade billingFacade
    ) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.saleReturnRepository = saleReturnRepository;
        this.saleMapper = saleMapper;
        this.saleReturnMapper = saleReturnMapper;
        this.customerFacade = customerFacade;
        this.productFacade = productFacade;
        this.inventoryFacade = inventoryFacade;
        this.userFacade = userFacade;
        this.billingFacade = billingFacade;
    }


    @Override
    @Transactional
    public SaleDto registerSale(CreateSaleCommand command) {
        return registerSale(command, null);
    }

    @Override
    @Transactional
    public SaleDto registerSale(CreateSaleCommand command, String idempotencyKey) {
        final String key = normalizeIdempotencyKey(idempotencyKey);
        final Long storeId = requireCurrentStoreId();

        // Fast-path de idempotencia: un reenvio secuencial (doble clic / reintento) con la
        // misma clave devuelve la venta original sin volver a descontar inventario ni facturar.
        if (key != null) {
            Optional<SaleDto> existing = saleRepository.findByIdempotencyKey(key)
                    .filter(s -> s.getStoreId().equals(storeId))
                    .map(saleMapper::toDto);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        if (command.items() == null || command.items().isEmpty()) {
            throw new IllegalArgumentException("La venta debe contener al menos un producto");
        }
        if (command.cashReceived() == null || command.cashReceived().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El efectivo recibido no puede ser negativo");
        }

        // 1. Resolve customer
        final Long resolvedCustomerId;
        Long customerId = command.customerId();
        if (customerId == null) {
            // El Cliente General es por local: si este local aun no tiene uno (p. ej. recien
            // se registro), se crea automaticamente en lugar de fallar la venta.
            CustomerDto generalCustomer = customerFacade.getByIdentification(GENERAL_CUSTOMER_IDENTIFICATION)
                    .orElseGet(() -> customerFacade.createCustomer(new CreateCustomerCommand(
                            "Cliente General", GENERAL_CUSTOMER_IDENTIFICATION, null, null, null)));
            resolvedCustomerId = generalCustomer.id();
        } else {
            final Long lookupId = customerId;
            CustomerDto customer = customerFacade.getById(lookupId)
                    .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado con ID: " + lookupId));
            if (!customer.active()) {
                throw new IllegalArgumentException("No se pueden realizar ventas a un cliente inactivo");
            }
            resolvedCustomerId = customer.id();
        }

        // Get current user
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null) ? auth.getName() : "admin";
        Long userId = userFacade.findByUsername(username)
                .map(u -> u.id())
                .orElse(1L);

        Sale sale = new Sale();
        sale.setStoreId(storeId);
        sale.setCustomerId(resolvedCustomerId);
        sale.setUserId(userId);
        sale.setTotalAmount(BigDecimal.ZERO);
        sale.setCashReceived(command.cashReceived());
        sale.setCashChange(BigDecimal.ZERO);
        sale.setPaymentMethod(normalizePaymentMethod(command.paymentMethod()));

        // Generate invoice number from database sequence
        Long seq = saleRepository.getNextInvoiceSeq();
        String invoiceNumber = "FACT-" + String.format("%06d", seq);
        sale.setInvoiceNumber(invoiceNumber);

        BigDecimal total = BigDecimal.ZERO;
        List<SaleItem> items = new ArrayList<>();

        for (CreateSaleItemCommand itemCommand : command.items()) {
            if (itemCommand.productId() == null) {
                throw new IllegalArgumentException("El ID del producto es obligatorio");
            }
            if (itemCommand.quantity() <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
            }

            // Verify product exists and is active
            ProductDto product = productFacade.getById(itemCommand.productId())
                    .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado con ID: " + itemCommand.productId()));
            if (!product.active()) {
                throw new IllegalArgumentException("El producto " + product.name() + " está inactivo y no se puede vender");
            }

            // Verify stock (RN-INV-001)
            if (product.quantityAvailable() < itemCommand.quantity()) {
                throw new IllegalArgumentException("Stock insuficiente para el producto: " + product.name() + " (Disponible: " + product.quantityAvailable() + ")");
            }

            // Register movement in Inventory module (descuenta stock internamente)
            inventoryFacade.registerMovement(itemCommand.productId(), "VENTA", itemCommand.quantity(), "Venta registrada Factura: " + invoiceNumber);

            BigDecimal subtotal = product.salePrice().multiply(BigDecimal.valueOf(itemCommand.quantity()));
            total = total.add(subtotal);

            BigDecimal taxRate = product.taxRate() != null ? product.taxRate() : BigDecimal.ZERO;
            BigDecimal taxFactor = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP));
            BigDecimal netSubtotal = subtotal.divide(taxFactor, 2, java.math.RoundingMode.HALF_UP);
            BigDecimal taxAmount = subtotal.subtract(netSubtotal);

            SaleItem saleItem = new SaleItem();
            saleItem.setSale(sale);
            saleItem.setProductId(itemCommand.productId());
            saleItem.setQuantity(itemCommand.quantity());
            saleItem.setUnitPrice(product.salePrice());
            saleItem.setSubtotal(subtotal);
            saleItem.setTaxRate(taxRate);
            saleItem.setTaxAmount(taxAmount);
            items.add(saleItem);
        }

        if (command.cashReceived().compareTo(total) < 0) {
            throw new IllegalArgumentException("El efectivo recibido (" + command.cashReceived() + ") es insuficiente para cubrir el total (" + total + ")");
        }

        sale.setTotalAmount(total);
        sale.setCashChange(command.cashReceived().subtract(total));
        sale.setItems(items);
        sale.setIdempotencyKey(key);

        // saveAndFlush fuerza la validacion de la restriccion unica (idempotency_key) ANTES
        // de facturar: si otro reenvio concurrente gano la carrera, la violacion aborta esta
        // transaccion (revirtiendo el descuento de inventario) antes de emitir a Factus.
        Sale saved = saleRepository.saveAndFlush(sale);
        SaleDto saleDto = saleMapper.toDto(saved);

        // Si sendToFactus es true, enviamos inmediatamente a Factus.
        // Si es false o no se envía, la venta queda guardada en POS para emisión manual posterior.
        if (Boolean.TRUE.equals(command.sendToFactus())) {
            try {
                billingFacade.processElectronicInvoice(saleDto);
            } catch (Exception e) {
                // Si la llamada remota a Factus falla, la venta local NO se revierte.
                // La factura electrónica queda registrada con estado PENDING para reintento/emisión posterior.
            }
        }

        return saleDto;
    }


    @Override
    public Optional<SaleDto> findByIdempotencyKey(String idempotencyKey) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        if (key == null) {
            return Optional.empty();
        }
        return saleRepository.findByIdempotencyKey(key)
                .filter(s -> s.getStoreId().equals(TenantContext.getStoreId()))
                .map(saleMapper::toDto);
    }

    @Override
    public Optional<SaleDto> getById(Long id) {
        return saleRepository.findById(id)
                .filter(s -> s.getStoreId().equals(TenantContext.getStoreId()))
                .map(saleMapper::toDto);
    }

    private static String normalizePaymentMethod(String paymentMethod) {
        return (paymentMethod == null || paymentMethod.isBlank()) ? "CASH" : paymentMethod.trim().toUpperCase();
    }

    private static String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public Page<SaleDto> searchSales(
            Long customerId,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            Pageable pageable
    ) {
        return saleRepository.searchSales(requireCurrentStoreId(), customerId, dateFrom, dateTo, pageable)
                .map(saleMapper::toDto);
    }

    @Override
    @Transactional
    public SaleReturnDto registerReturn(Long saleId, CreateSaleReturnCommand command) {
        Long storeId = requireCurrentStoreId();
        Sale sale = saleRepository.findById(saleId)
                .filter(s -> s.getStoreId().equals(storeId))
                .orElseThrow(() -> new EntityNotFoundException("Venta no encontrada con ID: " + saleId));

        if (command.items() == null || command.items().isEmpty()) {
            throw new IllegalArgumentException("La devolución debe contener al menos un ítem");
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null) ? auth.getName() : "admin";
        Long userId = userFacade.findByUsername(username)
                .map(u -> u.id())
                .orElse(1L);

        SaleReturn saleReturn = new SaleReturn();
        saleReturn.setSaleId(saleId);
        saleReturn.setStoreId(storeId);
        saleReturn.setUserId(userId);
        saleReturn.setReason(command.reason() != null ? command.reason().trim() : null);

        BigDecimal totalRefund = BigDecimal.ZERO;
        List<SaleReturnItem> items = new ArrayList<>();

        for (CreateSaleReturnItemCommand itemCommand : command.items()) {
            if (itemCommand.saleItemId() == null) {
                throw new IllegalArgumentException("El ID de la línea de venta es obligatorio");
            }
            if (itemCommand.quantity() <= 0) {
                throw new IllegalArgumentException("La cantidad a devolver debe ser mayor a cero");
            }

            SaleItem saleItem = saleItemRepository.findById(itemCommand.saleItemId())
                    .orElseThrow(() -> new EntityNotFoundException("Ítem de venta no encontrado con ID: " + itemCommand.saleItemId()));

            if (!saleItem.getSale().getId().equals(saleId)) {
                throw new IllegalArgumentException("El ítem " + itemCommand.saleItemId() + " no pertenece a la venta " + saleId);
            }

            int alreadyReturned = saleReturnRepository.sumReturnedQuantityBySaleItemId(itemCommand.saleItemId());
            int returnable = saleItem.getQuantity() - alreadyReturned;
            if (itemCommand.quantity() > returnable) {
                throw new IllegalArgumentException("No se puede devolver más de lo disponible para el ítem "
                        + itemCommand.saleItemId() + " (disponible para devolver: " + returnable + ")");
            }

            // Repone el stock: una devolución de CLIENTE incrementa inventario (distinto de
            // "RETURN"/"DEVOLUCION", que en este módulo significa devolución al proveedor).
            inventoryFacade.registerMovement(saleItem.getProductId(), "DEVOLUCION_VENTA", itemCommand.quantity(),
                    "Devolución de venta Factura: " + sale.getInvoiceNumber());

            BigDecimal subtotal = saleItem.getUnitPrice().multiply(BigDecimal.valueOf(itemCommand.quantity()));
            totalRefund = totalRefund.add(subtotal);

            SaleReturnItem returnItem = new SaleReturnItem();
            returnItem.setSaleReturn(saleReturn);
            returnItem.setSaleItemId(itemCommand.saleItemId());
            returnItem.setProductId(saleItem.getProductId());
            returnItem.setQuantity(itemCommand.quantity());
            returnItem.setUnitPrice(saleItem.getUnitPrice());
            returnItem.setSubtotal(subtotal);
            items.add(returnItem);
        }

        saleReturn.setTotalRefund(totalRefund);
        saleReturn.setItems(items);

        SaleReturn saved = saleReturnRepository.save(saleReturn);
        return saleReturnMapper.toDto(saved, sale.getInvoiceNumber());
    }

    @Override
    public List<SaleReturnDto> getReturnsBySale(Long saleId) {
        Long storeId = requireCurrentStoreId();
        Sale sale = saleRepository.findById(saleId)
                .filter(s -> s.getStoreId().equals(storeId))
                .orElseThrow(() -> new EntityNotFoundException("Venta no encontrada con ID: " + saleId));

        return saleReturnRepository.findAllBySaleIdOrderByCreatedAtDesc(saleId).stream()
                .map(r -> saleReturnMapper.toDto(r, sale.getInvoiceNumber()))
                .toList();
    }

    private static Long requireCurrentStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeId;
    }
}
