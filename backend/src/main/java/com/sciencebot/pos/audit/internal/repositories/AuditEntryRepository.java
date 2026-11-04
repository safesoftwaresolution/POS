package com.sciencebot.pos.audit.internal.repositories;

import com.sciencebot.pos.audit.internal.entities.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    Page<AuditEntry> findByStoreIdAndEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(
            Long storeId, String entityType, Long entityId, Pageable pageable);
}
