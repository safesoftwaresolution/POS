package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.internal.adapters.ActiveBillingProvider;
import com.sciencebot.pos.billing.internal.adapters.ElectronicInvoicingProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActiveBillingProviderTest {

    private static ElectronicInvoicingProvider provider(String name) {
        ElectronicInvoicingProvider provider = mock(ElectronicInvoicingProvider.class);
        when(provider.getProviderName()).thenReturn(name);
        return provider;
    }

    @Test
    void returnsTheProviderWhoseNameMatchesTheConfiguredOne() {
        ElectronicInvoicingProvider factus = provider("factus");
        ElectronicInvoicingProvider mock = provider("mock");

        assertSame(mock, new ActiveBillingProvider(List.of(factus, mock), "mock").get());
        assertSame(factus, new ActiveBillingProvider(List.of(factus, mock), "factus").get());
    }

    @Test
    void matchingIgnoresCaseAndSurroundingSpaces() {
        ElectronicInvoicingProvider factus = provider("Factus");

        ActiveBillingProvider active = new ActiveBillingProvider(List.of(factus), "  FACTUS ");

        assertSame(factus, active.get());
        assertEquals("factus", active.configuredName());
        assertTrue(active.isConfiguredProviderAvailable());
    }

    @Test
    void aNullConfiguredNameDefaultsToFactus() {
        ElectronicInvoicingProvider factus = provider("factus");

        assertSame(factus, new ActiveBillingProvider(List.of(factus), null).get());
    }

    @Test
    void anUnknownProviderFailsListingTheAvailableOnes() {
        ActiveBillingProvider active =
                new ActiveBillingProvider(List.of(provider("mock"), provider("factus")), "siigo");

        IllegalStateException ex = assertThrows(IllegalStateException.class, active::get);

        assertTrue(ex.getMessage().contains("'siigo'"));
        assertTrue(ex.getMessage().contains("[factus, mock]"), "debe listar los disponibles: " + ex.getMessage());
        assertFalse(active.isConfiguredProviderAvailable());
    }

    @Test
    void thereIsNoSilentFallbackToMockWhenFactusIsTheOnlyOneRegistered() {
        ActiveBillingProvider active = new ActiveBillingProvider(List.of(provider("factus")), "mock");

        assertThrows(IllegalStateException.class, active::get);
    }

    @Test
    void aBlankConfiguredNameIsNotAvailable() {
        ActiveBillingProvider active = new ActiveBillingProvider(List.of(provider("factus")), "  ");

        assertFalse(active.isConfiguredProviderAvailable());
        assertThrows(IllegalStateException.class, active::get);
    }

    @Test
    void withoutAnyRegisteredProviderGetFailsWithAnEmptyList() {
        ActiveBillingProvider active = new ActiveBillingProvider(List.of(), "factus");

        IllegalStateException ex = assertThrows(IllegalStateException.class, active::get);

        assertTrue(ex.getMessage().contains("[]"));
    }

    @Test
    void availableNamesAreSortedAndFirstRegisteredWinsOnDuplicates() {
        ElectronicInvoicingProvider first = provider("factus");
        ElectronicInvoicingProvider duplicate = provider("FACTUS");

        ActiveBillingProvider active = new ActiveBillingProvider(List.of(provider("mock"), first, duplicate), "factus");

        assertEquals(Set.of("factus", "mock"), active.availableNames());
        assertEquals(List.of("factus", "mock"), List.copyOf(active.availableNames()));
        assertSame(first, active.get());
    }
}
