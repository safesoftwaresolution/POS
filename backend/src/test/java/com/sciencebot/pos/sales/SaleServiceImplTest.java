package com.sciencebot.pos.sales;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.sales.internal.entities.Sale;
import com.sciencebot.pos.sales.internal.entities.SaleItem;
import com.sciencebot.pos.sales.internal.entities.SaleReturn;
import com.sciencebot.pos.sales.internal.repositories.SaleItemRepository;
import com.sciencebot.pos.sales.internal.repositories.SaleRepository;
import com.sciencebot.pos.sales.internal.repositories.SaleReturnRepository;
import com.sciencebot.pos.sales.internal.services.SaleServiceImpl;
import com.sciencebot.pos.sales.internal.mappers.SaleMapper;
import com.sciencebot.pos.sales.internal.mappers.SaleReturnMapper;
import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.services.ServiceDto;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.inventory.InventoryFacade;
import com.sciencebot.pos.users.UserFacade;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SaleServiceImplTest {

    private static final Long STORE_ID = 1L;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private SaleItemRepository saleItemRepository;

    @Mock
    private SaleReturnRepository saleReturnRepository;

    @Mock
    private SaleMapper saleMapper;

    @Mock
    private SaleReturnMapper saleReturnMapper;

    @Mock
    private CustomerFacade customerFacade;

    @Mock
    private ProductFacade productFacade;

    @Mock
    private ServiceFacade serviceFacade;

    @Mock
    private InventoryFacade inventoryFacade;

    @Mock
    private UserFacade userFacade;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SaleServiceImpl saleService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Sin sesion el servicio atribuye al usuario "admin"; el usuario debe existir (ya no hay id 1 por defecto).
        com.sciencebot.pos.users.UserDto actor = org.mockito.Mockito.mock(com.sciencebot.pos.users.UserDto.class);
        org.mockito.Mockito.when(actor.id()).thenReturn(1L);
        org.mockito.Mockito.when(userFacade.findByUsername("admin")).thenReturn(Optional.of(actor));
        TenantContext.setStoreId(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void registerSale_Success() {
        CreateSaleItemCommand item = new CreateSaleItemCommand(1L, 2);
        CreateSaleCommand command = new CreateSaleCommand(1L, BigDecimal.valueOf(20.00), true, List.of(item), "CASH");

        CustomerDto customer = new CustomerDto(1L, "Maria Lopez", "123", "a@a.com", "123", "Calle", true);
        when(customerFacade.getById(1L)).thenReturn(Optional.of(customer));

        ProductDto product = new ProductDto(1L, "P1", "123", "Prod 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.valueOf(8.00), 10, 2, true, null);
        when(productFacade.getById(1L)).thenReturn(Optional.of(product));
        when(saleRepository.getNextInvoiceSeq()).thenReturn(1L);

        Sale saved = new Sale();
        saved.setId(100L);
        saved.setInvoiceNumber("FACT-000001");
        saved.setTotalAmount(BigDecimal.valueOf(16.00));
        saved.setCashReceived(BigDecimal.valueOf(20.00));
        saved.setCashChange(BigDecimal.valueOf(4.00));
        when(saleRepository.saveAndFlush(any(Sale.class))).thenReturn(saved);

        SaleDto expectedDto = new SaleDto(100L, "FACT-000001", null, "Maria Lopez", BigDecimal.valueOf(16.00), BigDecimal.valueOf(20.00), BigDecimal.valueOf(4.00), "admin", List.of(), "CASH");
        when(saleMapper.toDto(any(Sale.class))).thenReturn(expectedDto);

        SaleDto result = saleService.registerSale(command);

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(16.00), result.totalAmount());
        assertEquals(BigDecimal.valueOf(4.00), result.cashChange());
        verify(inventoryFacade, times(1)).registerMovement(eq(1L), eq("VENTA"), eq(2), anyString());
        verify(saleRepository, times(1)).saveAndFlush(any(Sale.class));
        verify(eventPublisher, times(1)).publishEvent(any(SaleCompletedEvent.class));
    }

    @Test
    void registerSale_DuplicateIdempotencyKey_ReturnsExistingWithoutSideEffects() {
        CreateSaleItemCommand item = new CreateSaleItemCommand(1L, 2);
        CreateSaleCommand command = new CreateSaleCommand(1L, BigDecimal.valueOf(20.00), true, List.of(item), "CASH");

        Sale existing = new Sale();
        existing.setId(100L);
        existing.setStoreId(STORE_ID);
        existing.setInvoiceNumber("FACT-000001");
        existing.setIdempotencyKey("key-123");
        when(saleRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.of(existing));

        SaleDto existingDto = new SaleDto(100L, "FACT-000001", null, "Maria Lopez", BigDecimal.valueOf(16.00), BigDecimal.valueOf(20.00), BigDecimal.valueOf(4.00), "admin", List.of(), "CASH");
        when(saleMapper.toDto(existing)).thenReturn(existingDto);

        SaleDto result = saleService.registerSale(command, "key-123");

        assertNotNull(result);
        assertEquals(100L, result.id());
        // Reenvío idempotente: no descuenta inventario, no factura ni persiste una venta nueva.
        verify(inventoryFacade, never()).registerMovement(anyLong(), anyString(), anyInt(), anyString());
        verify(saleRepository, never()).saveAndFlush(any(Sale.class));
        verify(eventPublisher, never()).publishEvent(any(SaleCompletedEvent.class));
    }

    @Test
    void registerSale_InsufficientCash_ThrowsException() {
        CreateSaleItemCommand item = new CreateSaleItemCommand(1L, 2);
        CreateSaleCommand command = new CreateSaleCommand(1L, BigDecimal.valueOf(10.00), false, List.of(item), "CASH"); // Total is 16, cash is 10

        CustomerDto customer = new CustomerDto(1L, "Maria Lopez", "123", "a@a.com", "123", "Calle", true);
        when(customerFacade.getById(1L)).thenReturn(Optional.of(customer));

        ProductDto product = new ProductDto(1L, "P1", "123", "Prod 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.valueOf(8.00), 10, 2, true, null);
        when(productFacade.getById(1L)).thenReturn(Optional.of(product));
        when(saleRepository.getNextInvoiceSeq()).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> saleService.registerSale(command));
        assertTrue(ex.getMessage().contains("efectivo recibido"));
    }

    // --- Servicios ---

    private void stubSaleBasics() {
        CustomerDto customer = new CustomerDto(1L, "Maria Lopez", "123", "a@a.com", "123", "Calle", true);
        when(customerFacade.getById(1L)).thenReturn(Optional.of(customer));
        when(saleRepository.getNextInvoiceSeq()).thenReturn(1L);
        when(saleRepository.saveAndFlush(any(Sale.class))).thenAnswer(inv -> inv.getArgument(0));
        when(saleMapper.toDto(any(Sale.class))).thenReturn(mock(SaleDto.class));
    }

    private ServiceDto serviceOf(boolean active) {
        return new ServiceDto(2L, "SRV-1", "Masaje 60 min", null, 3L, "Spa", BigDecimal.valueOf(119000.00),
                BigDecimal.valueOf(19.00), "90111500", "HUR", 60, active);
    }

    @Test
    void registerSale_ServiceLine_DoesNotCheckStockNorMoveInventory() {
        stubSaleBasics();
        when(serviceFacade.getById(2L)).thenReturn(Optional.of(serviceOf(true)));
        CreateSaleCommand command = new CreateSaleCommand(1L, BigDecimal.valueOf(238000.00), false,
                List.of(new CreateSaleItemCommand(null, 2L, 2)), "CASH");

        saleService.registerSale(command);

        verify(inventoryFacade, never()).registerMovement(anyLong(), anyString(), anyInt(), anyString());
        verify(productFacade, never()).getById(anyLong());
        verify(saleRepository).saveAndFlush(argThat(s -> {
            SaleItem line = s.getItems().get(0);
            return line.getItemType() == SaleItemType.SERVICE
                    && line.getProductId() == null
                    && line.getServiceId().equals(2L)
                    && line.getItemName().equals("Masaje 60 min")
                    && "HUR".equals(line.getUnitMeasureCode())
                    && "90111500".equals(line.getUnspscCode())
                    && line.getSubtotal().compareTo(BigDecimal.valueOf(238000.00)) == 0
                    && line.getTaxAmount().compareTo(BigDecimal.valueOf(38000.00)) == 0
                    && s.getTotalAmount().compareTo(BigDecimal.valueOf(238000.00)) == 0;
        }));
    }

    @Test
    void registerSale_MixedProductAndService_OnlyProductMovesStock() {
        stubSaleBasics();
        ProductDto product = new ProductDto(1L, "P1", "123", "Prod 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.valueOf(8.00), 10, 2, true, null);
        when(productFacade.getById(1L)).thenReturn(Optional.of(product));
        when(serviceFacade.getById(2L)).thenReturn(Optional.of(serviceOf(true)));
        CreateSaleCommand command = new CreateSaleCommand(1L, BigDecimal.valueOf(200000.00), false,
                List.of(new CreateSaleItemCommand(1L, 1), new CreateSaleItemCommand(null, 2L, 1)), "CASH");

        saleService.registerSale(command);

        verify(inventoryFacade, times(1)).registerMovement(eq(1L), eq("VENTA"), eq(1), anyString());
        verify(saleRepository).saveAndFlush(argThat(s -> s.getItems().size() == 2
                && s.getTotalAmount().compareTo(BigDecimal.valueOf(119008.00)) == 0));
    }

    @Test
    void registerSale_InactiveService_ThrowsException() {
        stubSaleBasics();
        when(serviceFacade.getById(2L)).thenReturn(Optional.of(serviceOf(false)));
        CreateSaleCommand command = new CreateSaleCommand(1L, BigDecimal.valueOf(238000.00), false,
                List.of(new CreateSaleItemCommand(null, 2L, 1)), "CASH");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> saleService.registerSale(command));
        assertTrue(ex.getMessage().contains("inactivo"));
    }

    @Test
    void registerSale_ServiceFromAnotherStore_ThrowsNotFound() {
        stubSaleBasics();
        when(serviceFacade.getById(2L)).thenReturn(Optional.empty());
        CreateSaleCommand command = new CreateSaleCommand(1L, BigDecimal.valueOf(238000.00), false,
                List.of(new CreateSaleItemCommand(null, 2L, 1)), "CASH");

        assertThrows(EntityNotFoundException.class, () -> saleService.registerSale(command));
    }

    @Test
    void registerSale_LineWithBothProductAndService_ThrowsException() {
        CreateSaleCommand both = new CreateSaleCommand(1L, BigDecimal.TEN, false,
                List.of(new CreateSaleItemCommand(1L, 2L, 1)), "CASH");
        CreateSaleCommand neither = new CreateSaleCommand(1L, BigDecimal.TEN, false,
                List.of(new CreateSaleItemCommand(null, null, 1)), "CASH");
        stubSaleBasics();

        assertThrows(IllegalArgumentException.class, () -> saleService.registerSale(both));
        assertThrows(IllegalArgumentException.class, () -> saleService.registerSale(neither));
        verify(inventoryFacade, never()).registerMovement(anyLong(), anyString(), anyInt(), anyString());
    }

    @Test
    void registerReturn_ServiceLine_RefundsWithoutRestock() {
        Sale sale = saleOfStore(100L, STORE_ID);
        SaleItem saleItem = new SaleItem();
        saleItem.setId(5L);
        saleItem.setSale(sale);
        saleItem.setItemType(SaleItemType.SERVICE);
        saleItem.setServiceId(2L);
        saleItem.setQuantity(2);
        saleItem.setUnitPrice(BigDecimal.valueOf(119000.00));

        when(saleRepository.findById(100L)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findById(5L)).thenReturn(Optional.of(saleItem));
        when(saleReturnRepository.sumReturnedQuantityBySaleItemId(5L)).thenReturn(0);
        when(saleReturnRepository.save(any(SaleReturn.class))).thenAnswer(inv -> inv.getArgument(0));
        when(saleReturnMapper.toDto(any(SaleReturn.class), eq("FACT-000100"))).thenReturn(mock(SaleReturnDto.class));

        saleService.registerReturn(100L, new CreateSaleReturnCommand("Cancelado",
                List.of(new CreateSaleReturnItemCommand(5L, 1))));

        verify(inventoryFacade, never()).registerMovement(anyLong(), anyString(), anyInt(), anyString());
        verify(saleReturnRepository).save(argThat(r -> r.getTotalRefund().compareTo(BigDecimal.valueOf(119000.00)) == 0
                && r.getItems().get(0).getServiceId().equals(2L)
                && r.getItems().get(0).getProductId() == null));
    }

    // --- Devoluciones ---

    private Sale saleOfStore(Long saleId, Long storeId) {
        Sale sale = new Sale();
        sale.setId(saleId);
        sale.setStoreId(storeId);
        sale.setInvoiceNumber("FACT-000100");
        return sale;
    }

    private SaleItem saleItemOf(Long itemId, Sale sale, int quantity, BigDecimal unitPrice) {
        SaleItem item = new SaleItem();
        item.setId(itemId);
        item.setSale(sale);
        item.setProductId(1L);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        return item;
    }

    @Test
    void registerReturn_Success_RestocksAndComputesRefund() {
        Sale sale = saleOfStore(100L, STORE_ID);
        SaleItem saleItem = saleItemOf(1L, sale, 3, BigDecimal.valueOf(8.00));

        when(saleRepository.findById(100L)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findById(1L)).thenReturn(Optional.of(saleItem));
        when(saleReturnRepository.sumReturnedQuantityBySaleItemId(1L)).thenReturn(0);
        when(saleReturnRepository.save(any(SaleReturn.class))).thenAnswer(inv -> inv.getArgument(0));

        SaleReturnDto expectedDto = new SaleReturnDto(1L, 100L, "FACT-000100", "Defectuoso",
                BigDecimal.valueOf(16.00), "admin", null, List.of());
        when(saleReturnMapper.toDto(any(SaleReturn.class), eq("FACT-000100"))).thenReturn(expectedDto);

        CreateSaleReturnCommand command = new CreateSaleReturnCommand("Defectuoso",
                List.of(new CreateSaleReturnItemCommand(1L, 2)));

        SaleReturnDto result = saleService.registerReturn(100L, command);

        assertNotNull(result);
        verify(inventoryFacade, times(1)).registerMovement(eq(1L), eq("DEVOLUCION_VENTA"), eq(2), anyString());
        verify(saleReturnRepository, times(1)).save(argThat(r -> r.getTotalRefund().compareTo(BigDecimal.valueOf(16.00)) == 0));
    }

    @Test
    void registerReturn_ExceedsReturnableQuantity_ThrowsException() {
        Sale sale = saleOfStore(100L, STORE_ID);
        SaleItem saleItem = saleItemOf(1L, sale, 3, BigDecimal.valueOf(8.00));

        when(saleRepository.findById(100L)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findById(1L)).thenReturn(Optional.of(saleItem));
        // Ya se devolvieron 2 de las 3 unidades: solo queda 1 disponible para devolver.
        when(saleReturnRepository.sumReturnedQuantityBySaleItemId(1L)).thenReturn(2);

        CreateSaleReturnCommand command = new CreateSaleReturnCommand(null,
                List.of(new CreateSaleReturnItemCommand(1L, 2)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> saleService.registerReturn(100L, command));
        assertTrue(ex.getMessage().contains("disponible para devolver"));
        verify(inventoryFacade, never()).registerMovement(anyLong(), anyString(), anyInt(), anyString());
    }

    @Test
    void registerReturn_ItemBelongsToAnotherSale_ThrowsException() {
        Sale sale = saleOfStore(100L, STORE_ID);
        Sale otherSale = saleOfStore(999L, STORE_ID);
        SaleItem saleItem = saleItemOf(1L, otherSale, 3, BigDecimal.valueOf(8.00));

        when(saleRepository.findById(100L)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findById(1L)).thenReturn(Optional.of(saleItem));

        CreateSaleReturnCommand command = new CreateSaleReturnCommand(null,
                List.of(new CreateSaleReturnItemCommand(1L, 1)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> saleService.registerReturn(100L, command));
        assertTrue(ex.getMessage().contains("no pertenece"));
    }

    @Test
    void registerReturn_SaleFromAnotherStore_ThrowsNotFound() {
        Sale sale = saleOfStore(100L, 2L); // otro local
        when(saleRepository.findById(100L)).thenReturn(Optional.of(sale));

        CreateSaleReturnCommand command = new CreateSaleReturnCommand(null,
                List.of(new CreateSaleReturnItemCommand(1L, 1)));

        assertThrows(EntityNotFoundException.class, () -> saleService.registerReturn(100L, command));
    }
}
