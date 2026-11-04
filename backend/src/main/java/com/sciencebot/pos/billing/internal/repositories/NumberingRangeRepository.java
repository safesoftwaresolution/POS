package com.sciencebot.pos.billing.internal.repositories;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.entities.NumberingRange;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NumberingRangeRepository extends JpaRepository<NumberingRange, Long> {

    /** Rangos activos del local para un documento, bloqueados para escritura mientras se asigna un consecutivo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from NumberingRange r where r.storeId = :storeId and r.environment = :environment "
            + "and r.document = :document and r.active = true and r.deleted = false order by r.id")
    List<NumberingRange> findActiveForUpdate(@Param("storeId") Long storeId,
                                             @Param("environment") BillingEnvironment environment,
                                             @Param("document") String document);
}
