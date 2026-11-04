package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.adapters.ActiveBillingProvider;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraBillingAdapter;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberingRangeClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/** Con BILLING_PROVIDER=alegra y sin ningun token la app arranca y la cuenta figura como no configurada. */
@SpringBootTest(properties = {"billing.provider=alegra", "alegra.token.sandbox=", "alegra.token.production="})
class AlegraProviderStartupTest {

    @Autowired
    private ApplicationContext context;
    @Autowired
    private ActiveBillingProvider activeProvider;

    @Test
    void theAppStartsWithAlegraAndNoTokens() {
        assertInstanceOf(AlegraBillingAdapter.class, activeProvider.get());
        assertEquals("alegra", activeProvider.get().getProviderName());
        assertFalse(activeProvider.get().isAccountConfigured(1L, BillingEnvironment.SANDBOX));
        assertFalse(activeProvider.get().isAccountConfigured(1L, BillingEnvironment.PRODUCTION));
    }

    @Test
    void theAlegraBeansAreRegistered() {
        assertEquals(1, context.getBeanNamesForType(AlegraNumberAllocator.class).length);
        assertEquals(1, context.getBeanNamesForType(AlegraNumberingRangeClient.class).length);
    }
}
