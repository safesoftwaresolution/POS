package com.sciencebot.pos.billing.internal.repositories;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.entities.NumberingRange;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NumberingRangeRepository extends JpaRepository<NumberingRange, Long> {

    List<NumberingRange> findByStoreIdAndEnvironmentAndDeletedFalseOrderByIdAsc(Long storeId, BillingEnvironment environment);

    Optional<NumberingRange> findByIdAndStoreIdAndEnvironmentAndDeletedFalse(Long id, Long storeId, BillingEnvironment environment);

    /** Incluye los rangos eliminados: la unicidad de la resolución aplica aunque el rango esté marcado como borrado. */
    boolean existsByStoreIdAndEnvironmentAndDocumentAndPrefixAndResolutionNumber(
            Long storeId, BillingEnvironment environment, String document, String prefix, String resolutionNumber);

    /** El rango del local bloqueado para escritura: se usa al cambiar su estado o consecutivo mientras otros asignan números. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from NumberingRange r where r.id = :id and r.storeId = :storeId "
            + "and r.environment = :environment and r.deleted = false")
    Optional<NumberingRange> findForUpdate(@Param("id") Long id, @Param("storeId") Long storeId,
                                           @Param("environment") BillingEnvironment environment);

    /** Rangos activos del local para un documento, bloqueados para escritura mientras se asigna un consecutivo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from NumberingRange r where r.storeId = :storeId and r.environment = :environment "
            + "and r.document = :document and r.active = true and r.deleted = false order by r.id")
    List<NumberingRange> findActiveForUpdate(@Param("storeId") Long storeId,
                                             @Param("environment") BillingEnvironment environment,
                                             @Param("document") String document);
}
