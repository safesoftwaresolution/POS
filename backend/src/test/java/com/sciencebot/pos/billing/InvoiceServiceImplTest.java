package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceDto;
import com.sciencebot.pos.billing.internal.adapters.InvoiceProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.CustomerBillingData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.mock.MockBillingAdapter;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.mappers.BillingCanonicalMapper;
import com.sciencebot.pos.billing.internal.mappers.ElectronicInvoiceMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.billing.internal.services.InvoiceServiceImpl;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvoiceServiceImplTest {

    @Mock
    private ElectronicInvoiceRepository repository;

    @Mock
    private BillingCanonicalMapper canonicalMapper;

    @Mock
    private ElectronicInvoiceMapper invoiceMapper;

    @Mock
    private SaleFacade saleFacade;

    @Mock
    private InvoiceProvider customProvider;

    private MockBillingAdapter mockAdapter;
    private InvoiceServiceImpl invoiceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setStoreId(1L);
        mockAdapter = new MockBillingAdapter();

        when(customProvider.getProviderName()).thenReturn("factus");

        invoiceService = new InvoiceServiceImpl(
                repository,
                canonicalMapper,
                invoiceMapper,
                saleFacade,
                null,
                List.of(mockAdapter, customProvider)
        );
        ReflectionTestUtils.setField(invoiceService, "self", invoiceService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void processElectronicInvoice_WithMockProvider_Success() {
        ReflectionTestUtils.setField(invoiceService, "activeProviderName", "mock");

        SaleDto saleDto = new SaleDto(1L, "FACT-000001", null, "Cliente General", BigDecimal.valueOf(50.0), BigDecimal.valueOf(50.0), BigDecimal.ZERO, "admin", List.of(), "CASH");
        ElectronicInvoice pendingEntity = ElectronicInvoice.initialize(1L, 1L);
        pendingEntity.setId(10L);

        when(repository.findBySaleIdForUpdate(1L)).thenReturn(Optional.of(pendingEntity));
        when(repository.save(any(ElectronicInvoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerBillingData customerData = new CustomerBillingData("222222222222", "", "Consumidor Final", "a@a.com", 13, 21, null);
        InvoiceItemData itemData = new InvoiceItemData("REF-PROD", "Producto 1", 1, BigDecimal.valueOf(50.0), BigDecimal.ZERO, BigDecimal.valueOf(50.0));
        InvoiceRequest request = new InvoiceRequest(1L, "FACT-000001", "10", customerData, List.of(itemData), BigDecimal.valueOf(50.0));
        when(canonicalMapper.toInvoiceRequest(saleDto)).thenReturn(request);

        ElectronicInvoiceDto expectedDto = new ElectronicInvoiceDto(10L, 1L, "SETP-MOCK-000001", "mock-cufe", "mock-qr", "VALIDATED", null, "mock-pdf", LocalDateTime.now());
        when(invoiceMapper.toDto(any(ElectronicInvoice.class))).thenReturn(expectedDto);

        ElectronicInvoiceDto result = invoiceService.processElectronicInvoice(saleDto);

        assertNotNull(result);
        assertEquals("VALIDATED", result.status());
        assertEquals("SETP-MOCK-000001", result.factusNumber());
        verify(repository, atLeastOnce()).save(any(ElectronicInvoice.class));
    }

    @Test
    void processElectronicInvoice_AlreadyValidated_DoesNotCallProvider() {
        SaleDto saleDto = new SaleDto(1L, "FACT-000001", null, "Cliente General", BigDecimal.valueOf(50.0), BigDecimal.valueOf(50.0), BigDecimal.ZERO, "admin", List.of(), "CASH");
        ElectronicInvoice validatedEntity = ElectronicInvoice.initialize(1L, 1L);
        validatedEntity.setId(10L);
        validatedEntity.markAsValidated("SETP-001", "cufe", "qr", "pdf", LocalDateTime.now());

        when(repository.findBySaleIdForUpdate(1L)).thenReturn(Optional.of(validatedEntity));
        ElectronicInvoiceDto validatedDto = new ElectronicInvoiceDto(10L, 1L, "SETP-001", "cufe", "qr", "VALIDATED", null, "pdf", LocalDateTime.now());
        when(invoiceMapper.toDto(validatedEntity)).thenReturn(validatedDto);

        ElectronicInvoiceDto result = invoiceService.processElectronicInvoice(saleDto);

        assertNotNull(result);
        assertEquals("VALIDATED", result.status());
        verify(customProvider, never()).emitInvoice(any());
        verify(canonicalMapper, never()).toInvoiceRequest(any());
    }

    @Test
    void retryInvoice_AlreadyValidated_ThrowsException() {
        SaleDto saleDto = new SaleDto(1L, "FACT-000001", null, "Cliente General", BigDecimal.valueOf(50.0), BigDecimal.valueOf(50.0), BigDecimal.ZERO, "admin", List.of(), "CASH");
        when(saleFacade.getById(1L)).thenReturn(Optional.of(saleDto));

        ElectronicInvoice validatedEntity = ElectronicInvoice.initialize(1L, 1L);
        validatedEntity.setId(10L);
        validatedEntity.markAsValidated("SETP-001", "cufe", "qr", "pdf", LocalDateTime.now());
        when(repository.findBySaleIdAndStoreId(1L, 1L)).thenReturn(Optional.of(validatedEntity));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> invoiceService.retryInvoice(1L));

        assertTrue(ex.getMessage().contains("ya está validada"));
    }

    @Test
    void sendInvoiceEmail_Success() {
        ReflectionTestUtils.setField(invoiceService, "activeProviderName", "mock");

        ElectronicInvoice validated = ElectronicInvoice.initialize(1L, 1L);
        validated.setId(10L);
        validated.markAsValidated("SETP-001", "cufe", "qr", "pdf", LocalDateTime.now());
        when(repository.findBySaleIdAndStoreId(1L, 1L)).thenReturn(Optional.of(validated));

        assertDoesNotThrow(() -> invoiceService.sendInvoiceEmail(1L, "cliente@correo.com"));
    }

    @Test
    void sendInvoiceEmail_InvoiceNotFound_ThrowsException() {
        when(repository.findBySaleIdAndStoreId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> invoiceService.sendInvoiceEmail(99L, "cliente@correo.com"));
    }

    @Test
    void sendInvoiceEmail_InvoiceNotValidated_ThrowsException() {
        ElectronicInvoice pending = ElectronicInvoice.initialize(1L, 1L);
        pending.setId(10L);
        when(repository.findBySaleIdAndStoreId(1L, 1L)).thenReturn(Optional.of(pending));

        assertThrows(IllegalStateException.class,
                () -> invoiceService.sendInvoiceEmail(1L, "cliente@correo.com"));
    }

    @Test
    void processElectronicInvoice_WithoutStore_RefusesInsteadOfFallingBackToStoreOne() {
        TenantContext.clear();
        SaleDto saleDto = new SaleDto(1L, "FACT-000001", null, "Cliente General", BigDecimal.valueOf(50.0), BigDecimal.valueOf(50.0), BigDecimal.ZERO, "admin", List.of(), "CASH");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invoiceService.processElectronicInvoice(saleDto));

        assertTrue(ex.getMessage().contains("sin un local"));
        verify(repository, never()).saveAndFlush(any());
        verify(customProvider, never()).emitInvoice(any());
    }

    @Test
    void unknownProvider_FailsInsteadOfSilentlyUsingMock() {
        ReflectionTestUtils.setField(invoiceService, "activeProviderName", "factsus");
        ElectronicInvoice validated = ElectronicInvoice.initialize(1L, 1L);
        validated.markAsValidated("SETP-001", "cufe", "qr", "pdf", LocalDateTime.now());
        when(repository.findBySaleIdAndStoreId(1L, 1L)).thenReturn(Optional.of(validated));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invoiceService.sendInvoiceEmail(1L, "cliente@correo.com"));

        assertTrue(ex.getMessage().contains("factsus"));
    }

    @Test
    void downloadInvoicePDF_WithoutStore_IsRefused() {
        TenantContext.clear();

        assertThrows(IllegalStateException.class, () -> invoiceService.downloadInvoicePDF(1L));
        verify(repository, never()).findBySaleId(any());
    }
}