package com.sciencebot.pos.billing.internal.repositories;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.entities.DocumentNumberAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface DocumentNumberAllocationRepository extends JpaRepository<DocumentNumberAllocation, Long> {

    /**
     * Si el rango ya tiene números asignados, por cualquier local. Las asignaciones quedan a nombre del local del
     * documento, y con NIT compartido varias sucursales emiten contra el rango de la principal: filtrar por el
     * local del rango no vería las de las demás.
     */
    boolean existsByRangeId(Long rangeId);

    /** Mayor número ya asignado de un rango por cualquier local, o null si todavía no se asignó ninguno. */
    @Query("select max(a.number) from DocumentNumberAllocation a where a.rangeId = :rangeId")
    Long findMaxNumberByRangeId(@Param("rangeId") Long rangeId);

    Optional<DocumentNumberAllocation> findByStoreIdAndEnvironmentAndDocumentAndReferenceCode(
            Long storeId, BillingEnvironment environment, String document, String referenceCode);

    /** The allocation id is internal; callers obtained it with the store-scoped allocation lookup. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DocumentNumberAllocation a where a.id = :id and a.storeId = :storeId")
    Optional<DocumentNumberAllocation> findAllocationForUpdateAndStoreId(@Param("id") Long id, @Param("storeId") Long storeId);

    @Query("select a.storeId from DocumentNumberAllocation a where a.id = :id")
    Optional<Long> findStoreIdById(@Param("id") Long id);
}
