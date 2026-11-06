package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.adapters.dto.CompanyProfileData;
import com.sciencebot.pos.billing.internal.adapters.dto.DianEnablementStatus;
import com.sciencebot.pos.billing.internal.adapters.dto.DianResolutionData;
import com.sciencebot.pos.billing.internal.adapters.mock.MockOnboardingProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** El proveedor simulado deja recorrer el alta completa sin red: empresa, habilitación y resolución de prueba. */
class MockOnboardingProviderTest {

    private final MockOnboardingProvider provider = new MockOnboardingProvider(
            Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneId.of("America/Bogota")));

    private static CompanyProfileData profile(String nit) {
        return new CompanyProfileData("Tienda Don Pepe SAS", null, nit, "8", 1, "31", "R-99-PN", List.of("4711"),
                "a@b.co", null, "Calle 1", "05001", "05");
    }

    @Test
    void createCompanyReturnsADeterministicIdFromTheNit() {
        assertEquals("mock-company-901234567", provider.createCompany(BillingEnvironment.PRODUCTION, profile("901234567")));
        assertEquals("mock-company-901234567", provider.createCompany(BillingEnvironment.PRODUCTION, profile("901234567")));
        assertDoesNotThrow(() -> provider.updateCompany(BillingEnvironment.PRODUCTION, "mock-company-901234567",
                profile("901234567")));
    }

    @Test
    void theCompanyIsAuthorizedRightAfterStartingTheEnablement() {
        String companyId = provider.createCompany(BillingEnvironment.PRODUCTION, profile("901234567"));
        provider.startDianEnablement(BillingEnvironment.PRODUCTION, companyId, "test-set-id");

        DianEnablementStatus status = provider.readEnablement(BillingEnvironment.PRODUCTION, companyId);
        assertTrue(status.isAuthorized());
        assertEquals("AUTHORIZED", status.rawValue());
    }

    @Test
    void listsOneTestResolutionValidAroundToday() {
        assertEquals(List.of(new DianResolutionData("18760000001", "SETP", 990000000L, 995000000L,
                        LocalDate.of(2025, 11, 6), LocalDate.of(2027, 11, 6), "fc8eac422eba16e22ffd8c6f94b3f40a6e38162c")),
                provider.listResolutions(BillingEnvironment.PRODUCTION, "901234567"));
    }

    @Test
    void thePlatformIsAlwaysReady() {
        assertTrue(provider.isPlatformReady(BillingEnvironment.SANDBOX));
        assertTrue(provider.isPlatformReady(BillingEnvironment.PRODUCTION));
    }
}
