package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusBillingAdapter;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusInvoiceClient;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusNumberingRangeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FactusBillingAdapterTest {

    @Mock
    private FactusInvoiceClient invoiceClient;

    @Mock
    private FactusNumberingRangeClient rangeClient;

    private FactusBillingAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new FactusBillingAdapter(invoiceClient, rangeClient);
    }

    @Test
    void getProviderName_ReturnsFactus() {
        assertEquals("factus", adapter.getProviderName());
    }

    // --- Pruebas de delegación hacia FactusInvoiceClient ---

    @Test
    void emitInvoice_DelegatesToInvoiceClient() {
        InvoiceRequest request = new InvoiceRequest(1L, "FACT-001", "10", null, List.of(), BigDecimal.TEN);
        InvoiceResult expectedResult = InvoiceResult.success("FACT-001", "cufe-123", "qr-url", "public-url");

        when(invoiceClient.emitInvoice(request)).thenReturn(expectedResult);

        InvoiceResult result = adapter.emitInvoice(request);

        assertSame(expectedResult, result);
        verify(invoiceClient).emitInvoice(request);
    }

    @Test
    void queryInvoice_DelegatesToInvoiceClient() {
        InvoiceResult expectedResult = InvoiceResult.success("FACT-001", "cufe-123", "qr-url", "public-url");
        when(invoiceClient.queryInvoice("FACT-001")).thenReturn(expectedResult);

        InvoiceResult result = adapter.queryInvoice("FACT-001");

        assertSame(expectedResult, result);
        verify(invoiceClient).queryInvoice("FACT-001");
    }

    @Test
    void downloadInvoicePDF_DelegatesToInvoiceClient() {
        byte[] expectedPdf = new byte[]{1, 2, 3};
        when(invoiceClient.downloadInvoicePDF("FACT-001")).thenReturn(expectedPdf);

        byte[] result = adapter.downloadInvoicePDF("FACT-001");

        assertSame(expectedPdf, result);
        verify(invoiceClient).downloadInvoicePDF("FACT-001");
    }

    @Test
    void downloadInvoiceXML_DelegatesToInvoiceClient() {
        byte[] expectedXml = new byte[]{4, 5, 6};
        when(invoiceClient.downloadInvoiceXML("FACT-001")).thenReturn(expectedXml);

        byte[] result = adapter.downloadInvoiceXML("FACT-001");

        assertSame(expectedXml, result);
        verify(invoiceClient).downloadInvoiceXML("FACT-001");
    }

    @Test
    void deleteUnvalidatedInvoice_DelegatesToInvoiceClient() {
        when(invoiceClient.deleteUnvalidatedInvoice("FACT-001")).thenReturn(true);

        boolean result = adapter.deleteUnvalidatedInvoice("FACT-001");

        assertTrue(result);
        verify(invoiceClient).deleteUnvalidatedInvoice("FACT-001");
    }

    @Test
    void sendInvoiceEmail_DelegatesToInvoiceClient() {
        when(invoiceClient.sendInvoiceEmail("FACT-001", "cliente@tienda.com")).thenReturn(true);

        boolean result = adapter.sendInvoiceEmail("FACT-001", "cliente@tienda.com");

        assertTrue(result);
        verify(invoiceClient).sendInvoiceEmail("FACT-001", "cliente@tienda.com");
    }

    // --- Pruebas de delegación hacia FactusNumberingRangeClient ---

    @Test
    void queryDianNumberingRanges_DelegatesToRangeClient() {
        List<NumberingRangeDto> expectedList = List.of();
        when(rangeClient.queryDianNumberingRanges()).thenReturn(expectedList);

        List<NumberingRangeDto> result = adapter.queryDianNumberingRanges();

        assertSame(expectedList, result);
        verify(rangeClient).queryDianNumberingRanges();
    }

    @Test
    void listNumberingRanges_DelegatesToRangeClient() {
        List<NumberingRangeDto> expectedList = List.of();
        when(rangeClient.listNumberingRanges()).thenReturn(expectedList);

        List<NumberingRangeDto> result = adapter.listNumberingRanges();

        assertSame(expectedList, result);
        verify(rangeClient).listNumberingRanges();
    }

    @Test
    void getNumberingRange_DelegatesToRangeClient() {
        NumberingRangeDto expectedDto = new NumberingRangeDto(1L, "01", "SETP", "18764", 1L, 1000L, 1L, "2026-01-01", "2027-01-01", "key-xyz", true);
        when(rangeClient.getNumberingRange(1L)).thenReturn(expectedDto);

        NumberingRangeDto result = adapter.getNumberingRange(1L);

        assertSame(expectedDto, result);
        verify(rangeClient).getNumberingRange(1L);
    }

    @Test
    void createNumberingRange_DelegatesToRangeClient() {
        CreateNumberingRangeRequest request = new CreateNumberingRangeRequest(
                "01", "SETP", "18764000001234", 1L, "2026-01-01", "2027-01-01", 1L, 5000L, "key-xyz"
        );
        NumberingRangeDto expectedDto = new NumberingRangeDto(2L, "01", "SETP", "18764000001234", 1L, 5000L, 1L, "2026-01-01", "2027-01-01", "key-xyz", true);
        when(rangeClient.createNumberingRange(request)).thenReturn(expectedDto);

        NumberingRangeDto result = adapter.createNumberingRange(request);

        assertSame(expectedDto, result);
        verify(rangeClient).createNumberingRange(request);
    }

    @Test
    void deleteNumberingRange_DelegatesToRangeClient() {
        when(rangeClient.deleteNumberingRange(1L)).thenReturn(true);

        boolean result = adapter.deleteNumberingRange(1L);

        assertTrue(result);
        verify(rangeClient).deleteNumberingRange(1L);
    }

    @Test
    void toggleNumberingRangeStatus_DelegatesToRangeClient() {
        when(rangeClient.toggleNumberingRangeStatus(1L)).thenReturn(true);

        boolean result = adapter.toggleNumberingRangeStatus(1L);

        assertTrue(result);
        verify(rangeClient).toggleNumberingRangeStatus(1L);
    }
}
