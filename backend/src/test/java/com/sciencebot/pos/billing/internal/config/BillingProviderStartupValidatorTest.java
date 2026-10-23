package com.sciencebot.pos.billing.internal.config;

import com.sciencebot.pos.billing.internal.adapters.ActiveBillingProvider;
import com.sciencebot.pos.billing.internal.adapters.ElectronicInvoicingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BillingProviderStartupValidatorTest {

    private static ElectronicInvoicingProvider provider(String name) {
        ElectronicInvoicingProvider provider = mock(ElectronicInvoicingProvider.class);
        when(provider.getProviderName()).thenReturn(name);
        return provider;
    }

    private static BillingProviderStartupValidator validatorFor(String configured, String... registered) {
        List<ElectronicInvoicingProvider> providers = java.util.Arrays.stream(registered)
                .map(BillingProviderStartupValidatorTest::provider)
                .toList();
        return new BillingProviderStartupValidator(new ActiveBillingProvider(providers, configured));
    }

    @Test
    void startsWhenTheConfiguredProviderIsRegistered() {
        assertDoesNotThrow(validatorFor("factus", "factus")::validate);
        assertDoesNotThrow(validatorFor("mock", "factus", "mock")::validate);
    }

    @Test
    void failsToStartWithAnUnknownProviderListingTheAvailableOnes() {
        BillingProviderStartupValidator validator = validatorFor("siigo", "factus", "mock");

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(ex.getMessage().contains("siigo"));
        assertTrue(ex.getMessage().contains("[factus, mock]"), "debe listar los disponibles: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("BILLING_PROVIDER"));
    }

    @Test
    void failsToStartWhenMockIsRequestedButNotRegistered() {
        // MockBillingAdapter solo se registra con billing.provider=mock; sin él no hay respaldo.
        assertThrows(IllegalStateException.class, validatorFor("mock", "factus")::validate);
    }

    @Test
    void failsToStartWithABlankProvider() {
        assertThrows(IllegalStateException.class, validatorFor("", "factus")::validate);
    }

    // --- Con el cableado real de Spring (@Value + @PostConstruct) ---

    @Configuration
    @Import({ActiveBillingProvider.class, BillingProviderStartupValidator.class})
    static class WiringConfig {
        @Bean
        ElectronicInvoicingProvider factusStub() {
            return provider("factus");
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(WiringConfig.class);

    @Test
    void theApplicationContextDoesNotStartWithBillingProviderSiigoAndTheErrorListsTheAvailableOnes() {
        runner.withPropertyValues("billing.provider=siigo").run(context -> {
            assertThat(context).hasFailed();
            Throwable root = context.getStartupFailure();
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertThat(root).isInstanceOf(IllegalStateException.class);
            assertThat(root.getMessage()).contains("siigo").contains("[factus]");
        });
    }

    @Test
    void theApplicationContextStartsWithTheConfiguredProviderOrTheFactusDefault() {
        runner.withPropertyValues("billing.provider=factus").run(context -> assertThat(context).hasNotFailed());
        runner.run(context -> assertThat(context).hasNotFailed());
    }
}
