package com.sciencebot.pos.inventory.internal.repositories;

import com.sciencebot.pos.inventory.internal.entities.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    
    @Query("SELECT m FROM InventoryMovement m WHERE m.storeId = :storeId AND " +
           "(:productId IS NULL OR m.productId = :productId) AND " +
           "(:movementType IS NULL OR m.movementType = :movementType) AND " +
           "(cast(:dateFrom as timestamp) IS NULL OR m.createdAt >= :dateFrom) AND " +
           "(cast(:dateTo as timestamp) IS NULL OR m.createdAt <= :dateTo)")
    Page<InventoryMovement> searchMovements(
            @Param("storeId") Long storeId,
            @Param("productId") Long productId,
            @Param("movementType") String movementType,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable);
}
