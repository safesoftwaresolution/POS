package com.sciencebot.pos.billing.internal.repositories;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreBillingConfigRepository extends JpaRepository<StoreBillingConfig, Long> {

    /**
     * La configuración del local con lock de escritura: las transiciones de etapa se serializan, de modo que dos
     * solicitudes simultáneas (por ejemplo, dos consultas del asistente que ven todo listo) no activan dos veces ni
     * avisan dos veces al local.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM StoreBillingConfig c WHERE c.storeId = :storeId")
    Optional<StoreBillingConfig> findByIdForUpdate(@Param("storeId") Long storeId);

    /** Locales en una etapa dada del proceso (para la cola de revisión del back-office). */
    List<StoreBillingConfig> findByStatusOrderByUpdatedAtAsc(BillingActivationStatus status);
}
