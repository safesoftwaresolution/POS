package com.sciencebot.pos.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface InventoryFacade {
    InventoryMovementDto registerMovement(Long productId, String movementType, int quantity, String reason);

    /**
     * Igual que la anterior; si {@code expirationDate} no es null, la entrada (ENTRY/ENTRADA/COMPRA) guarda además un
     * lote con esas unidades y ese vencimiento, en la misma transacción. En cualquier otro tipo de movimiento falla
     * con {@link IllegalArgumentException}. {@code lotCode} es opcional.
     */
    InventoryMovementDto registerMovement(Long productId, String movementType, int quantity, String reason,
                                          LocalDate expirationDate, String lotCode);
    Page<InventoryMovementDto> searchMovements(
            Long productId,
            String movementType,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            Pageable pageable
    );
}
