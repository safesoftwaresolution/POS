package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.api.*;
import com.sciencebot.pos.billing.dto.*;
import com.sciencebot.pos.billing.exception.*;
import com.sciencebot.pos.billing.internal.services.BillingServiceImpl;
import com.sciencebot.pos.billing.internal.services.InvoiceService;
import com.sciencebot.pos.billing.internal.services.NumberingRangeService;
import com.sciencebot.pos.sales.SaleDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillingServiceImplTest {

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private NumberingRangeService numberingRangeService;

    private BillingServiceImpl billingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        billingService = new BillingServiceImpl(invoiceService, numberingRangeService);
    }

    @Test
    void processElectronicInvoice_DelegatesToInvoiceService() {
        SaleDto saleDto = new SaleDto(1L, "FACT-000001", null, "Cliente General", BigDecimal.valueOf(50.0), BigDecimal.valueOf(50.0), BigDecimal.ZERO, "admin", List.of(), "CASH");
        ElectronicInvoiceDto expected = new ElectronicInvoiceDto(10L, 1L, "SETP-001", "cufe", "qr", "VALIDATED", null, "pdf", LocalDateTime.now());
        when(invoiceService.processElectronicInvoice(saleDto)).thenReturn(expected);

        ElectronicInvoiceDto result = billingService.processElectronicInvoice(saleDto);

        assertNotNull(result);
        assertEquals("VALIDATED", result.status());
        verify(invoiceService).processElectronicInvoice(saleDto);
    }

    @Test
    void getBySaleId_DelegatesToInvoiceService() {
        ElectronicInvoiceDto expected = new ElectronicInvoiceDto(10L, 1L, "SETP-001", "cufe", "qr", "VALIDATED", null, "pdf", LocalDateTime.now());
        when(invoiceService.getBySaleId(1L)).thenReturn(Optional.of(expected));

        Optional<ElectronicInvoiceDto> result = billingService.getBySaleId(1L);

        assertTrue(result.isPresent());
        assertEquals("SETP-001", result.get().factusNumber());
        verify(invoiceService).getBySaleId(1L);
    }

    @Test
    void retryInvoice_DelegatesToInvoiceService() {
        ElectronicInvoiceDto expected = new ElectronicInvoiceDto(10L, 1L, "SETP-001", "cufe", "qr", "VALIDATED", null, "pdf", LocalDateTime.now());
        when(invoiceService.retryInvoice(1L)).thenReturn(expected);

        ElectronicInvoiceDto result = billingService.retryInvoice(1L);

        assertNotNull(result);
        assertEquals("SETP-001", result.factusNumber());
        verify(invoiceService).retryInvoice(1L);
    }

    @Test
    void sendInvoiceEmail_DelegatesToInvoiceService() {
        doNothing().when(invoiceService).sendInvoiceEmail(1L, "test@mail.com");

        billingService.sendInvoiceEmail(1L, "test@mail.com");

        verify(invoiceService).sendInvoiceEmail(1L, "test@mail.com");
    }

    @Test
    void numberingRangeOperations_DelegateToNumberingRangeService() {
        NumberingRangeDto rangeDto = new NumberingRangeDto(1L, "01", "SETP", "18764000001234", 1L, 5000L, 1L, "2026-01-01", "2027-01-01", "key", true);

        when(numberingRangeService.queryDianNumberingRanges()).thenReturn(List.of(rangeDto));
        when(numberingRangeService.listNumberingRanges()).thenReturn(List.of(rangeDto));
        when(numberingRangeService.getNumberingRange(1L)).thenReturn(rangeDto);
        NumberingRangeDto toggled = new NumberingRangeDto(1L, "01", "SETP", "18764000001234", 1L, 5000L, 1L, "2026-01-01", "2027-01-01", "key", false);
        when(numberingRangeService.toggleNumberingRangeStatus(1L)).thenReturn(toggled);
        NumberingRangeDto adjusted = new NumberingRangeDto(1L, "01", "SETP", "18764000001234", 1L, 5000L, 150L, "2026-01-01", "2027-01-01", "key", true);
        when(numberingRangeService.updateNumberingRangeCurrent(1L, 150L)).thenReturn(adjusted);

        CreateNumberingRangeRequest createReq = new CreateNumberingRangeRequest("01", "SETP", "18764000001234", 1L);
        when(numberingRangeService.createNumberingRange(createReq)).thenReturn(rangeDto);

        assertEquals(1, billingService.queryDianNumberingRanges().size());
        assertEquals(1, billingService.listNumberingRanges().size());
        assertEquals(rangeDto, billingService.getNumberingRange(1L));
        assertEquals(rangeDto, billingService.createNumberingRange(createReq));
        billingService.deleteNumberingRange(1L);
        assertEquals(toggled, billingService.toggleNumberingRangeStatus(1L));
        assertEquals(adjusted, billingService.updateNumberingRangeCurrent(1L, 150L));

        verify(numberingRangeService).queryDianNumberingRanges();
        verify(numberingRangeService).listNumberingRanges();
        verify(numberingRangeService).getNumberingRange(1L);
        verify(numberingRangeService).createNumberingRange(createReq);
        verify(numberingRangeService).deleteNumberingRange(1L);
        verify(numberingRangeService).toggleNumberingRangeStatus(1L);
        verify(numberingRangeService).updateNumberingRangeCurrent(1L, 150L);
    }
}
