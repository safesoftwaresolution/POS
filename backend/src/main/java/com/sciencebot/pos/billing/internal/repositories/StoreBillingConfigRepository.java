package com.sciencebot.pos.billing.internal.repositories;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreBillingConfigRepository extends JpaRepository<StoreBillingConfig, Long> {

    /** Locales en una etapa dada del proceso (para la cola de revisión del back-office). */
    List<StoreBillingConfig> findByStatusOrderByUpdatedAtAsc(BillingActivationStatus status);
}
