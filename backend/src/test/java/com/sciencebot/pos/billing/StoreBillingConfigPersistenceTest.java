package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Verifica con Hibernate y H2 reales que la configuración de facturación del local se guarda y se lee completa. */
@SpringBootTest
@Transactional
class StoreBillingConfigPersistenceTest {

    @Autowired
    private StoreBillingConfigRepository repository;

    @Autowired
    private EntityManager em;

    @Test
    void theRangeAndTheCompanyIdOfEachEnvironmentRoundTrip() {
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(9001L);
        config.assignNumberingRangeId(BillingEnvironment.SANDBOX, 14);
        config.assignNumberingRangeId(BillingEnvironment.PRODUCTION, 15);
        config.assignCompanyId(BillingEnvironment.SANDBOX, "company-sandbox");
        config.assignCompanyId(BillingEnvironment.PRODUCTION, "company-production");
        repository.saveAndFlush(config);

        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT sandbox_numbering_range_id, production_numbering_range_id, sandbox_company_id, "
                        + "production_company_id FROM store_billing_config WHERE store_id = 9001").getSingleResult();
        assertEquals(14, ((Number) row[0]).intValue());
        assertEquals(15, ((Number) row[1]).intValue());
        assertEquals("company-sandbox", row[2]);
        assertEquals("company-production", row[3]);

        em.clear();
        StoreBillingConfig reloaded = repository.findById(9001L).orElseThrow();
        assertEquals(14, reloaded.numberingRangeIdFor(BillingEnvironment.SANDBOX));
        assertEquals(15, reloaded.numberingRangeIdFor(BillingEnvironment.PRODUCTION));
        assertEquals("company-sandbox", reloaded.companyIdFor(BillingEnvironment.SANDBOX));
        assertEquals("company-production", reloaded.companyIdFor(BillingEnvironment.PRODUCTION));
        assertEquals(BillingActivationStatus.NOT_REQUESTED, reloaded.getStatus());
        assertEquals(BillingEnvironment.SANDBOX, reloaded.getEnvironment());
        assertNotNull(reloaded.getCreatedAt());
    }
}
