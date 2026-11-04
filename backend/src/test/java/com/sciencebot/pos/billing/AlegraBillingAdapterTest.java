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
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceClient;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNoteClient;
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
    private AlegraInvoiceClient invoiceClient;
    private AlegraNoteClient noteClient;
    private AlegraBillingAdapter adapter;

    @BeforeEach
    void setUp() {
        resolver = mock(AlegraAccessResolver.class);
        companyProvider = mock(AlegraCompanyProvider.class);
        rangeClient = mock(AlegraNumberingRangeClient.class);
        invoiceClient = mock(AlegraInvoiceClient.class);
        noteClient = mock(AlegraNoteClient.class);
        adapter = new AlegraBillingAdapter(resolver, companyProvider, rangeClient, invoiceClient, noteClient);
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
    void invoiceEmissionAndReconciliationGoToTheInvoiceClient() {
        InvoiceResult emitted = InvoiceResult.pending("x");
        when(invoiceClient.emitInvoice(null)).thenReturn(emitted);
        when(invoiceClient.queryInvoiceByProviderId("inv-1")).thenReturn(emitted);

        assertSame(emitted, adapter.emitInvoice(null));
        assertSame(emitted, adapter.queryInvoiceByProviderId("inv-1"));
    }

    @Test
    void noteEmissionGoesToTheNoteClient() {
        CreditNoteResult credit = CreditNoteResult.pending("x");
        DebitNoteResult debit = DebitNoteResult.pending("y");
        when(noteClient.emitCreditNote(null, 5L)).thenReturn(credit);
        when(noteClient.emitDebitNote(null, 5L)).thenReturn(debit);

        assertSame(credit, adapter.emitCreditNote(null, 5L));
        assertSame(debit, adapter.emitDebitNote(null, 5L));
    }

    @Test
    void queryingAnInvoiceByLegalNumberIsNotSupportedAndSaysSoWithoutThrowing() {
        InvoiceResult query = adapter.queryInvoice("SETP1");

        assertEquals("ERROR", query.status());
        assertTrue(query.errorMessage().contains("no está implementad"), query.errorMessage());
        assertFalse(query.errorMessage().contains("409"), "los servicios de notas leen 409 como conflicto de Factus");
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
        AlegraCompany company = new AlegraCompany("c1", "Nombre", null, "900", "1", null, null, null, null, null, null, Map.of());
        assertEquals("c1", company.id());
    }

    @Test
    void thePdfIsDownloadedByTheProviderDocumentId() {
        when(invoiceClient.downloadInvoicePDF("inv-1")).thenReturn(new byte[]{1, 2, 3});

        assertArrayEquals(new byte[]{1, 2, 3}, adapter.downloadInvoicePDFByProviderId("inv-1"));
    }

    @Test
    void emailAndXmlGoToTheInvoiceClientByProviderId() {
        when(invoiceClient.sendInvoiceEmail("inv-1", "a@b.co")).thenReturn(true);
        when(invoiceClient.downloadInvoiceXML("inv-1")).thenReturn(new byte[]{4});

        assertTrue(adapter.sendInvoiceEmailByProviderId("inv-1", "a@b.co"));
        assertArrayEquals(new byte[]{4}, adapter.downloadInvoiceXMLByProviderId("inv-1"));
    }
}
