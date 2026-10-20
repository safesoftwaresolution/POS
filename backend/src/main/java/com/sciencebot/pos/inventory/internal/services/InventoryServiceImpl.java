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
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryFacade {

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

        int previousStock = product.quantityAvailable();
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

        int newStock = previousStock + delta;
        if (newStock < 0) {
            throw new IllegalArgumentException("Inventario insuficiente. El stock resultante no puede ser negativo (RN-INV-001)");
        }

        // Apply stock update in products module
        productFacade.updateStock(productId, delta);

        // El stock acaba de cruzar el minimo hacia abajo (antes estaba por encima, ahora no):
        // se avisa una sola vez en el momento en que ocurre, no en cada movimiento subsiguiente
        // mientras siga por debajo.
        if (newStock <= product.minStock() && previousStock > product.minStock()) {
            lowStockAlertNotifier.notifyLowStock(requireCurrentStoreId(), product, newStock);
        }

        // Get current user
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null) ? auth.getName() : "admin";
        Long userId = userFacade.findByUsername(username)
                .map(u -> u.id())
                .orElse(1L);    

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
