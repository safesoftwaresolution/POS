package com.sciencebot.pos.billing.internal.repositories;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.entities.DocumentNumberAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentNumberAllocationRepository extends JpaRepository<DocumentNumberAllocation, Long> {

    Optional<DocumentNumberAllocation> findByStoreIdAndEnvironmentAndDocumentAndReferenceCode(
            Long storeId, BillingEnvironment environment, String document, String referenceCode);
}
