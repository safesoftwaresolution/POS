package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.internal.listeners.ElectronicInvoiceEventListener;
import com.sciencebot.pos.billing.internal.services.InvoiceService;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.sales.SaleCompletedEvent;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ElectronicInvoiceEventListenerTest {

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private SaleFacade saleFacade;

    private ElectronicInvoiceEventListener listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new ElectronicInvoiceEventListener(invoiceService, saleFacade);
    }

    @Test
    void onSaleCompleted_SendToFactusTrue_ProcessesInvoice() {
        SaleCompletedEvent event = new SaleCompletedEvent(
                10L,
                1L,
                "FACT-000001",
                BigDecimal.valueOf(100.0),
                true,
                LocalDateTime.now()
        );

        SaleDto saleDto = new SaleDto(10L, "FACT-000001", null, "Cliente General", BigDecimal.valueOf(100.0), BigDecimal.valueOf(100.0), BigDecimal.ZERO, "admin", List.of(), "CASH");
        when(saleFacade.getById(10L)).thenReturn(Optional.of(saleDto));

        listener.onSaleCompleted(event);

        verify(saleFacade, times(1)).getById(10L);
        verify(invoiceService, times(1)).processElectronicInvoice(saleDto);
    }

    @Test
    void onSaleCompleted_SendToFactusFalse_IgnoresEvent() {
        SaleCompletedEvent event = new SaleCompletedEvent(
                10L,
                1L,
                "FACT-000001",
                BigDecimal.valueOf(100.0),
                false,
                LocalDateTime.now()
        );

        listener.onSaleCompleted(event);

        verify(saleFacade, never()).getById(anyLong());
        verify(invoiceService, never()).processElectronicInvoice(any());
    }

    @Test
    void onSaleCompleted_SaleNotFound_DoesNotThrow() {
        SaleCompletedEvent event = new SaleCompletedEvent(
                99L,
                1L,
                "FACT-000099",
                BigDecimal.valueOf(100.0),
                true,
                LocalDateTime.now()
        );

        when(saleFacade.getById(99L)).thenReturn(Optional.empty());

        listener.onSaleCompleted(event);

        verify(saleFacade, times(1)).getById(99L);
        verify(invoiceService, never()).processElectronicInvoice(any());
    }
}
