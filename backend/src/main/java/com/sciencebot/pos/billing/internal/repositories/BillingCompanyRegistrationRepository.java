package com.sciencebot.pos.billing.internal.repositories;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.entities.BillingCompanyRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BillingCompanyRegistrationRepository extends JpaRepository<BillingCompanyRegistration, Long> {

    Optional<BillingCompanyRegistration> findByStoreIdAndEnvironmentAndProviderName(
            Long storeId, BillingEnvironment environment, String providerName);

    boolean existsByEnvironmentAndProviderNameAndProviderCompanyIdAndStoreIdNot(
            BillingEnvironment environment, String providerName, String providerCompanyId, Long storeId);
}
