package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.exception.AlegraApiException;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccess;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccessResolver;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraBillingAdapter;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraCompany;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraCompanyProvider;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberingRangeClient;
import com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult;
import com.sciencebot.pos.billing.internal.adapters.dto.DebitNoteResult;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlegraBillingAdapterTest {

    private AlegraAccessResolver resolver;
    private AlegraCompanyProvider companyProvider;
    private AlegraNumberingRangeClient rangeClient;
    private AlegraBillingAdapter adapter;

    @BeforeEach
    void setUp() {
        resolver = mock(AlegraAccessResolver.class);
        companyProvider = mock(AlegraCompanyProvider.class);
        rangeClient = mock(AlegraNumberingRangeClient.class);
        adapter = new AlegraBillingAdapter(resolver, companyProvider, rangeClient);
    }

    private static AlegraAccess access() {
        return new AlegraAccess(5L, BillingEnvironment.SANDBOX, "https://sandbox.example", "tok", "company-5");
    }

    @Test
    void identifiesItselfAsAlegra() {
        assertEquals("alegra", adapter.getProviderName());
        assertEquals("Alegra", adapter.displayName());
    }

    @Test
    void isAccountConfigured_DelegatesToTheResolver() {
        when(resolver.isConfigured(5L, BillingEnvironment.SANDBOX)).thenReturn(true);

        assertTrue(adapter.isAccountConfigured(5L, BillingEnvironment.SANDBOX));
        assertFalse(adapter.isAccountConfigured(6L, BillingEnvironment.SANDBOX));
    }

    @Test
    void verifyConnection_ReadsTheCompanyFreshWithTheStoresAccessIgnoringItsStatus() {
        AlegraAccess access = access();
        when(resolver.resolveIgnoringStatus(5L, BillingEnvironment.SANDBOX)).thenReturn(access);
        when(companyProvider.fetch(access)).thenReturn(mock(AlegraCompany.class));

        assertDoesNotThrow(() -> adapter.verifyConnection(5L, BillingEnvironment.SANDBOX));

        verify(companyProvider).fetch(access);
    }

    @Test
    void verifyConnection_PropagatesAnInvalidTokenAndAnUnconfiguredAccount() {
        AlegraAccess access = access();
        when(resolver.resolveIgnoringStatus(5L, BillingEnvironment.SANDBOX)).thenReturn(access);
        when(companyProvider.fetch(access)).thenThrow(new AlegraApiException(401, "Alegra rechazó el token", null));
        assertThrows(AlegraApiException.class, () -> adapter.verifyConnection(5L, BillingEnvironment.SANDBOX));

        when(resolver.resolveIgnoringStatus(6L, BillingEnvironment.SANDBOX))
                .thenThrow(new BillingNotEnabledException("Falta el company.id"));
        assertThrows(BillingNotEnabledException.class, () -> adapter.verifyConnection(6L, BillingEnvironment.SANDBOX));
    }

    @Test
    void onAccountChanged_DropsTheCachedCompany() {
        adapter.onAccountChanged(5L);
        verify(companyProvider).invalidate(5L);
    }

    @Test
    void documentEmissionIsNotImplementedYetAndSaysSoWithoutThrowing() {
        InvoiceResult invoice = adapter.emitInvoice(null);
        CreditNoteResult credit = adapter.emitCreditNote(null, 5L);
        DebitNoteResult debit = adapter.emitDebitNote(null, 5L);
        InvoiceResult query = adapter.queryInvoice("SETP1");

        for (String message : List.of(invoice.errorMessage(), credit.errorMessage(), debit.errorMessage(), query.errorMessage())) {
            assertTrue(message.contains("no está implementad"), message);
            assertFalse(message.contains("409"), "los servicios de notas leen 409 como conflicto de Factus");
        }
        assertFalse(invoice.isSuccess());
        assertEquals("ERROR", invoice.status());
    }

    @Test
    void numberingRangeOperationsGoToTheLocalRangeClient() {
        NumberingRangeDto dto = new NumberingRangeDto(1L, "01", "SETP", "1876", 1L, 10L, 1L, null, null, null, true);
        CreateNumberingRangeRequest request = new CreateNumberingRangeRequest("01", "SETP", "1876", 1L);
        when(rangeClient.listNumberingRanges()).thenReturn(List.of(dto));
        when(rangeClient.getNumberingRange(1L)).thenReturn(dto);
        when(rangeClient.createNumberingRange(request)).thenReturn(dto);
        when(rangeClient.toggleNumberingRangeStatus(1L)).thenReturn(dto);
        when(rangeClient.updateNumberingRangeCurrent(1L, 5L)).thenReturn(dto);
        when(rangeClient.queryDianNumberingRanges()).thenThrow(new UnsupportedOperationException("pendiente"));

        assertEquals(List.of(dto), adapter.listNumberingRanges());
        assertEquals(dto, adapter.getNumberingRange(1L));
        assertEquals(dto, adapter.createNumberingRange(request));
        assertEquals(dto, adapter.toggleNumberingRangeStatus(1L));
        assertEquals(dto, adapter.updateNumberingRangeCurrent(1L, 5L));
        adapter.deleteNumberingRange(1L);
        verify(rangeClient).deleteNumberingRange(1L);
        assertThrows(UnsupportedOperationException.class, () -> adapter.queryDianNumberingRanges());
    }

    @Test
    void companyDataComesFromTheCompanyProviderRecord() {
        AlegraCompany company = new AlegraCompany("c1", "Nombre", null, "900", "1", null, null, null, null, null, Map.of());
        assertEquals("c1", company.id());
    }
}
