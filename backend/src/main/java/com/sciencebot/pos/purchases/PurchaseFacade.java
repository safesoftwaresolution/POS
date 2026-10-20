package com.sciencebot.pos.purchases;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PurchaseFacade {
    PurchaseDto registerPurchase(CreatePurchaseCommand command);

    /**
     * Registra una compra de forma idempotente. Si {@code idempotencyKey} no es nulo y ya
     * existe una compra con esa clave, devuelve la compra original sin crear un duplicado
     * ni incrementar inventario de nuevo.
     */
    PurchaseDto registerPurchase(CreatePurchaseCommand command, String idempotencyKey);

    /** Recupera una compra por su clave de idempotencia (para resolver reenvios concurrentes). */
    Optional<PurchaseDto> findByIdempotencyKey(String idempotencyKey);

    Optional<PurchaseDto> getById(Long id);
    Page<PurchaseDto> searchPurchases(
            Long supplierId,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            Pageable pageable
    );
}
