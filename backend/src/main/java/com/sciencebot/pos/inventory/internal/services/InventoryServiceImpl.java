package com.sciencebot.pos.inventory.internal.services;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.inventory.*;
import com.sciencebot.pos.inventory.internal.entities.InventoryMovement;
import com.sciencebot.pos.inventory.internal.repositories.InventoryMovementRepository;
import com.sciencebot.pos.inventory.internal.mappers.InventoryMapper;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.users.UserFacade;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryFacade {

    /** Movimientos que suben stock con mercancía nueva: los únicos que pueden traer un vencimiento (lote). */
    private static final Set<String> ENTRY_TYPES = Set.of("ENTRADA", "ENTRY", "COMPRA");

    private final InventoryMovementRepository movementRepository;
    private final InventoryMapper inventoryMapper;
    private final ProductFacade productFacade;
    private final UserFacade userFacade;
    private final LowStockAlertNotifier lowStockAlertNotifier;

    public InventoryServiceImpl(
            InventoryMovementRepository movementRepository,
            InventoryMapper inventoryMapper,
            @Lazy ProductFacade productFacade,
            @Lazy UserFacade userFacade,
            LowStockAlertNotifier lowStockAlertNotifier
    ) {
        this.movementRepository = movementRepository;
        this.inventoryMapper = inventoryMapper;
        this.productFacade = productFacade;
        this.userFacade = userFacade;
        this.lowStockAlertNotifier = lowStockAlertNotifier;
    }

    @Override
    @Transactional
    public InventoryMovementDto registerMovement(Long productId, String movementType, int quantity, String reason) {
        return registerMovement(productId, movementType, quantity, reason, true);
    }

    /**
     * Igual que {@link #registerMovement(Long, String, int, String)} pero permite no avisar de stock bajo.
     * La importación masiva lo usa con {@code alertLowStock = false}: un conteo puede dejar decenas de
     * productos bajo el mínimo (una ráfaga de correos) y además corre en una transacción que puede revertirse,
     * mientras que la alerta es asíncrona y saldría igual. Uso interno del módulo (no está en la fachada);
     * es público para que pase por el proxy transaccional.
     */
    @Transactional
    public InventoryMovementDto registerMovement(Long productId, String movementType, int quantity, String reason,
                                                 boolean alertLowStock) {
        return registerMovement(productId, movementType, quantity, reason, alertLowStock, null, null);
    }

    @Override
    @Transactional
    public InventoryMovementDto registerMovement(Long productId, String movementType, int quantity, String reason,
                                                 LocalDate expirationDate, String lotCode) {
        return registerMovement(productId, movementType, quantity, reason, true, expirationDate, lotCode);
    }

    /**
     * Núcleo de todos los movimientos. Si llega {@code expirationDate} (solo válida en una entrada) se guarda un lote con
     * estas unidades en la misma transacción: si el lote falla (fecha absurda, código largo) no queda stock subido.
     */
    @Transactional
    public InventoryMovementDto registerMovement(Long productId, String movementType, int quantity, String reason,
                                                 boolean alertLowStock, LocalDate expirationDate, String lotCode) {
        if (productId == null) {
            throw new IllegalArgumentException("El ID del producto es obligatorio");
        }
        if (movementType == null || movementType.isBlank()) {
            throw new IllegalArgumentException("El tipo de movimiento es obligatorio");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }

        ProductDto product = productFacade.getById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado con ID: " + productId));

        int delta;
        String typeUpper = movementType.toUpperCase().trim();

        switch (typeUpper) {
            case "ENTRADA":
            case "ENTRY":
            case "COMPRA":
            case "AJUSTE":
            case "ADJUSTMENT":
            case "DEVOLUCION_VENTA": // Cliente devuelve mercancía: repone stock (distinto de RETURN/DEVOLUCION, que es al proveedor).
                delta = quantity;
                break;
            case "SALIDA":
            case "EXIT":
            case "VENTA":
            case "RETURN":
            case "DEVOLUCION":
                delta = -quantity;
                break;
            default:
                throw new IllegalArgumentException("Tipo de movimiento inválido: " + movementType);
        }

        // Validacion temprana con la lectura de arriba (da el mensaje claro); la garantia real contra la
        // sobreventa es el UPDATE atomico condicionado de adjustStock, que no depende de esta lectura.
        if (product.quantityAvailable() + delta < 0) {
            throw new IllegalArgumentException("Inventario insuficiente. El stock resultante no puede ser negativo (RN-INV-001)");
        }

        if (delta > 0) {
            // Antes de subir el stock se fija en los lotes lo que ya se vendió: si no, un lote agotado hace tiempo
            // volvería a mostrar unidades en cuanto el stock subiera (una entrada sin fecha, un conteo, una devolución).
            productFacade.consolidateLots(productId);
        }

        if (expirationDate != null) {
            if (!ENTRY_TYPES.contains(typeUpper)) {
                throw new IllegalArgumentException("El vencimiento solo se puede registrar en una entrada de mercancía");
            }
            productFacade.addLot(productId, quantity, expirationDate, lotCode);
        }

        // Apply stock update in products module (atomico). El stock resultante sale de la BD, no de la lectura
        // inicial: con ventas concurrentes esa lectura puede estar desfasada y falsearia el kardex.
        int newStock = productFacade.adjustStock(productId, delta);
        int previousStock = newStock - delta;

        // El stock acaba de cruzar el minimo hacia abajo (antes estaba por encima, ahora no):
        // se avisa una sola vez en el momento en que ocurre, no en cada movimiento subsiguiente
        // mientras siga por debajo.
        if (alertLowStock && newStock <= product.minStock() && previousStock > product.minStock()) {
            lowStockAlertNotifier.notifyLowStock(requireCurrentStoreId(), product, newStock);
        }

        // Get current user
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null) ? auth.getName() : "admin";
        Long userId = userFacade.findByUsername(username)
                .map(u -> u.id())
                .orElseThrow(() -> new IllegalStateException("No se pudo identificar al usuario autenticado"));

        InventoryMovement movement = new InventoryMovement();
        movement.setStoreId(requireCurrentStoreId());
        movement.setProductId(productId);
        movement.setMovementType(typeUpper);
        movement.setQuantity(quantity);
        movement.setPreviousStock(previousStock);
        movement.setNewStock(newStock);
        movement.setReason(reason != null ? reason.trim() : null);
        movement.setUserId(userId);

        InventoryMovement saved = movementRepository.save(movement);
        return inventoryMapper.toDto(saved);
    }

    @Override
    public Page<InventoryMovementDto> searchMovements(
            Long productId,
            String movementType,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            Pageable pageable
    ) {
        String cleanType = (movementType == null || movementType.isBlank()) ? null : movementType.toUpperCase().trim();
        return movementRepository.searchMovements(requireCurrentStoreId(), productId, cleanType, dateFrom, dateTo, pageable)
                .map(inventoryMapper::toDto);
    }

    private static Long requireCurrentStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeId;
    }
}
