package com.sciencebot.pos.sales.internal.repositories;

import com.sciencebot.pos.sales.internal.entities.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Query(value = "SELECT nextval('invoice_seq')", nativeQuery = true)
    Long getNextInvoiceSeq();

    Optional<Sale> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT s FROM Sale s WHERE s.storeId = :storeId AND " +
           "(:customerId IS NULL OR s.customerId = :customerId) AND " +
           "(cast(:dateFrom as timestamp) IS NULL OR s.createdAt >= :dateFrom) AND " +
           "(cast(:dateTo as timestamp) IS NULL OR s.createdAt <= :dateTo)")
    Page<Sale> searchSales(
            @Param("storeId") Long storeId,
            @Param("customerId") Long customerId,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable);
}
