package com.sciencebot.pos.stores.internal.repositories;

import com.sciencebot.pos.stores.internal.entities.StoreUserAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreUserAuditRepository extends JpaRepository<StoreUserAuditEntity, Long> {
}
