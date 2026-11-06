package com.sciencebot.pos.billing.internal.repositories;

import com.sciencebot.pos.billing.internal.entities.BillingCompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

/** Datos fiscales de cada local; la clave es el id del local. */
public interface BillingCompanyProfileRepository extends JpaRepository<BillingCompanyProfile, Long> {
}
