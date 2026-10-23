package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.adapters.mock.MockBillingAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Puerto de cuenta del adaptador mock: sin credenciales ni red. */
class MockBillingAdapterAccountTest {

    private final MockBillingAdapter adapter = new MockBillingAdapter();

    @Test
    void accountIsAlwaysConfiguredInBothEnvironments() {
        assertTrue(adapter.isAccountConfigured(1L, BillingEnvironment.SANDBOX));
        assertTrue(adapter.isAccountConfigured(1L, BillingEnvironment.PRODUCTION));
    }

    @Test
    void verifyConnectionAndAccountChangedAreNoOps() {
        assertDoesNotThrow(() -> adapter.verifyConnection(1L, BillingEnvironment.SANDBOX));
        assertDoesNotThrow(() -> adapter.onAccountChanged(1L));
        assertDoesNotThrow(() -> adapter.onAccountChanged(null));
    }

    @Test
    void displayNameIsMock() {
        assertEquals("Mock", adapter.displayName());
    }
}
