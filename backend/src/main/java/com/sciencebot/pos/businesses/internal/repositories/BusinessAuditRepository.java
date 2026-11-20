package com.sciencebot.pos.businesses.internal.repositories;

import com.sciencebot.pos.businesses.internal.entities.BusinessAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessAuditRepository extends JpaRepository<BusinessAuditEntity, Long> {

    java.util.List<BusinessAuditEntity> findByBusinessIdOrderByIdAsc(Long businessId);
}
