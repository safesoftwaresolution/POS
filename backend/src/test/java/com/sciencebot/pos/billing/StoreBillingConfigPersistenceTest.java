package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.entities.BillingCompanyProfile;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.repositories.BillingCompanyProfileRepository;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
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

    @Autowired
    private BillingCompanyProfileRepository profiles;

    @Autowired
    private ElectronicInvoiceRepository invoices;

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

    @Test
    void theFiscalProfileOfTheStoreRoundTrips() {
        BillingCompanyProfile profile = new BillingCompanyProfile();
        profile.setStoreId(9002L);
        profile.setLegalName("Tienda La Esquina S.A.S.");
        profile.setTradeName("La Esquina");
        profile.setNit("900123456");
        profile.setDv("7");
        profile.setOrganizationType(1);
        profile.setRegimeCode("RESPONSABLE_DE_IVA");
        profile.setEconomicActivity("4711");
        profile.setEmail("facturas@esquina.co");
        profile.setPhone("3001234567");
        profile.setAddress("Calle 10 # 5-20");
        profile.setCityCode("05001");
        profile.setDepartmentCode("05");
        profiles.saveAndFlush(profile);

        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT legal_name, nit, dv, identification_type, organization_type, city_code, department_code "
                        + "FROM billing_company_profile WHERE store_id = 9002").getSingleResult();
        assertEquals("Tienda La Esquina S.A.S.", row[0]);
        assertEquals("900123456", row[1]);
        assertEquals("7", String.valueOf(row[2]));
        assertEquals("31", row[3], "el tipo de identificación por defecto es NIT (31)");
        assertEquals(1, ((Number) row[4]).intValue());
        assertEquals("05001", row[5]);
        assertEquals("05", row[6]);

        em.clear();
        BillingCompanyProfile reloaded = profiles.findById(9002L).orElseThrow();
        assertEquals("La Esquina", reloaded.getTradeName());
        assertEquals("RESPONSABLE_DE_IVA", reloaded.getRegimeCode());
        assertEquals("4711", reloaded.getEconomicActivity());
        assertEquals("facturas@esquina.co", reloaded.getEmail());
        assertEquals("3001234567", reloaded.getPhone());
        assertEquals("Calle 10 # 5-20", reloaded.getAddress());
        assertNotNull(reloaded.getCreatedAt());
        assertNotNull(reloaded.getUpdatedAt());
    }

    @Test
    void theEnvironmentOfAnInvoiceRoundTrips() {
        ElectronicInvoice invoice = ElectronicInvoice.initialize(9003L, 9003L);
        invoice.assignEnvironmentIfMissing(BillingEnvironment.PRODUCTION);
        Long id = invoices.saveAndFlush(invoice).getId();

        assertEquals("PRODUCTION", em.createNativeQuery(
                "SELECT environment FROM electronic_invoices WHERE id = " + id).getSingleResult());

        em.clear();
        assertEquals(BillingEnvironment.PRODUCTION, invoices.findById(id).orElseThrow().getEnvironment());
    }
}
