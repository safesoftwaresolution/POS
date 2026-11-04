package com.sciencebot.pos.billing.internal.repositories;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.entities.DocumentNumberAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocumentNumberAllocationRepository extends JpaRepository<DocumentNumberAllocation, Long> {

    boolean existsByStoreIdAndRangeId(Long storeId, Long rangeId);

    /** Mayor número ya asignado de un rango, o null si todavía no se asignó ninguno. */
    @Query("select max(a.number) from DocumentNumberAllocation a where a.storeId = :storeId and a.rangeId = :rangeId")
    Long findMaxNumberByStoreIdAndRangeId(@Param("storeId") Long storeId, @Param("rangeId") Long rangeId);

    Optional<DocumentNumberAllocation> findByStoreIdAndEnvironmentAndDocumentAndReferenceCode(
            Long storeId, BillingEnvironment environment, String document, String referenceCode);
}
