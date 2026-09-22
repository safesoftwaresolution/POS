package com.sciencebot.pos.billing.internal.repositories;

import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreBillingConfigRepository extends JpaRepository<StoreBillingConfig, Long> {
}
