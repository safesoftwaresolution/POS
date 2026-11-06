package com.sciencebot.pos.billing.internal.adapters.mock;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.adapters.BillingOnboardingProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.CompanyProfileData;
import com.sciencebot.pos.billing.internal.adapters.dto.DianEnablementStatus;
import com.sciencebot.pos.billing.internal.adapters.dto.DianResolutionData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Alta simulada ante el proveedor, sin red ni estado: la empresa recibe un id derivado del NIT, figura habilitada
 * ante la DIAN (también tras reiniciar la app, porque no guarda nada en memoria) y tiene una resolución de prueba
 * (SETP) vigente un año hacia atrás y uno hacia adelante. Sirve para recorrer el asistente completo en local y en
 * los tests.
 */
@Component
@ConditionalOnProperty(name = "billing.provider", havingValue = "mock")
public class MockOnboardingProvider implements BillingOnboardingProvider {

    private static final String COMPANY_PREFIX = "mock-company-";

    private final Clock clock;

    @Autowired
    public MockOnboardingProvider() {
        this(Clock.systemDefaultZone());
    }

    public MockOnboardingProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String createCompany(BillingEnvironment environment, CompanyProfileData profile) {
        Objects.requireNonNull(profile, "Los datos fiscales de la empresa son obligatorios");
        return COMPANY_PREFIX + profile.identification();
    }

    @Override
    public void updateCompany(BillingEnvironment environment, String companyId, CompanyProfileData profile) {
        // Nada que actualizar: no hay proveedor externo.
    }

    @Override
    public void startDianEnablement(BillingEnvironment environment, String companyId, String testSetId) {
        // La DIAN simulada habilita de inmediato: readEnablement responde AUTHORIZED.
    }

    @Override
    public DianEnablementStatus readEnablement(BillingEnvironment environment, String companyId) {
        return DianEnablementStatus.of("AUTHORIZED");
    }

    @Override
    public List<DianResolutionData> listResolutions(BillingEnvironment environment, String nit) {
        LocalDate today = LocalDate.now(clock);
        return List.of(new DianResolutionData("18760000001", "SETP", 990000000L, 995000000L,
                today.minusYears(1), today.plusYears(1), "fc8eac422eba16e22ffd8c6f94b3f40a6e38162c"));
    }

    @Override
    public boolean isPlatformReady(BillingEnvironment environment) {
        return true;
    }
}
