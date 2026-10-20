package com.sciencebot.pos.purchases.internal.repositories;

import com.sciencebot.pos.purchases.internal.entities.Purchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Optional<Purchase> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT p FROM Purchase p WHERE p.storeId = :storeId AND " +
           "(:supplierId IS NULL OR p.supplierId = :supplierId) AND " +
           "(cast(:dateFrom as timestamp) IS NULL OR p.createdAt >= :dateFrom) AND " +
           "(cast(:dateTo as timestamp) IS NULL OR p.createdAt <= :dateTo)")
    Page<Purchase> searchPurchases(
            @Param("storeId") Long storeId,
            @Param("supplierId") Long supplierId,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable);
}
