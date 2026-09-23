package com.sciencebot.pos.billing.internal.repositories;

import com.sciencebot.pos.billing.internal.entities.NumberingRangeAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NumberingRangeAuditRepository extends JpaRepository<NumberingRangeAudit, Long> {

    List<NumberingRangeAudit> findByStoreIdAndRangeIdOrderByCreatedAtDesc(Long storeId, Long rangeId);
}
