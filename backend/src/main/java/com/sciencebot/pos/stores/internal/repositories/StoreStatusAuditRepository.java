package com.sciencebot.pos.stores.internal.repositories;

import com.sciencebot.pos.stores.internal.entities.StoreStatusAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreStatusAuditRepository extends JpaRepository<StoreStatusAuditEntity, Long> {
}
