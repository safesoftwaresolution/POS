package com.sciencebot.pos.inventory;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.inventory.internal.entities.InventoryMovement;
import com.sciencebot.pos.inventory.internal.repositories.InventoryMovementRepository;
import com.sciencebot.pos.inventory.internal.services.InventoryServiceImpl;
import com.sciencebot.pos.inventory.internal.mappers.InventoryMapper;
import com.sciencebot.pos.inventory.internal.services.LowStockAlertNotifier;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.users.UserFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class InventoryServiceImplTest {

    @Mock
    private InventoryMovementRepository movementRepository;

    @Mock
    private InventoryMapper inventoryMapper;

    @Mock
    private ProductFacade productFacade;

    @Mock
    private UserFacade userFacade;

    @Mock
    private LowStockAlertNotifier lowStockAlertNotifier;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Sin sesion el servicio atribuye al usuario "admin"; el usuario debe existir (ya no hay id 1 por defecto).
        com.sciencebot.pos.users.UserDto actor = org.mockito.Mockito.mock(com.sciencebot.pos.users.UserDto.class);
        org.mockito.Mockito.when(actor.id()).thenReturn(1L);
        org.mockito.Mockito.when(userFacade.findByUsername("admin")).thenReturn(Optional.of(actor));
        TenantContext.setStoreId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void registerMovement_Entry_Success() {
        ProductDto product = new ProductDto(1L, "P1", "123", "Product 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.TEN, 10, 5, true, null);
        when(productFacade.getById(1L)).thenReturn(Optional.of(product));

        InventoryMovement saved = new InventoryMovement();
        saved.setId(100L);
        saved.setProductId(1L);
        saved.setMovementType("ENTRADA");
        saved.setQuantity(5);
        saved.setPreviousStock(10);
        saved.setNewStock(15);
        when(movementRepository.save(any(InventoryMovement.class))).thenReturn(saved);

        InventoryMovementDto expectedDto = new InventoryMovementDto(100L, 1L, "Product 1", "ENTRADA", 5, 10, 15, "Ajuste", "admin", null);
        when(inventoryMapper.toDto(any(InventoryMovement.class))).thenReturn(expectedDto);

        when(productFacade.adjustStock(1L, 5)).thenReturn(15);

        InventoryMovementDto result = inventoryService.registerMovement(1L, "ENTRADA", 5, "Ajuste");

        assertNotNull(result);
        assertEquals(15, result.newStock());
        verify(productFacade, times(1)).adjustStock(1L, 5);
        verify(movementRepository, times(1)).save(any(InventoryMovement.class));
    }

    @Test
    void registerMovement_EntryWithExpiration_SavesTheLotAndRaisesTheStock() {
        ProductDto product = new ProductDto(1L, "P1", "123", "Product 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.TEN, 10, 5, true, null);
        when(productFacade.getById(1L)).thenReturn(Optional.of(product));
        when(productFacade.adjustStock(1L, 50)).thenReturn(60);
        when(movementRepository.save(any(InventoryMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        java.time.LocalDate expiration = java.time.LocalDate.of(2027, 1, 15);

        inventoryService.registerMovement(1L, "ENTRY", 50, "Cargamento", expiration, "L-2210");

        verify(productFacade).addLot(1L, 50, expiration, "L-2210");
        verify(productFacade).adjustStock(1L, 50);
    }

    @Test
    void registerMovement_PurchaseWithExpiration_SavesTheLot() {
        ProductDto product = new ProductDto(1L, "P1", "123", "Product 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.TEN, 10, 5, true, null);
        when(productFacade.getById(1L)).thenReturn(Optional.of(product));
        when(productFacade.adjustStock(1L, 20)).thenReturn(30);
        when(movementRepository.save(any(InventoryMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        java.time.LocalDate expiration = java.time.LocalDate.of(2027, 2, 1);

        inventoryService.registerMovement(1L, "COMPRA", 20, "Compra", expiration, null);

        verify(productFacade).addLot(1L, 20, expiration, null);
    }

    @Test
    void registerMovement_EntryWithoutExpiration_SavesNoLot() {
        ProductDto product = new ProductDto(1L, "P1", "123", "Product 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.TEN, 10, 5, true, null);
        when(productFacade.getById(1L)).thenReturn(Optional.of(product));
        when(productFacade.adjustStock(1L, 5)).thenReturn(15);
        when(movementRepository.save(any(InventoryMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.registerMovement(1L, "ENTRY", 5, "Sin fecha", null, null);

        verify(productFacade, never()).addLot(any(), anyInt(), any(), any());
    }

    @Test
    void registerMovement_EveryStockIncrease_FixesTheSoldLotsBeforeRaisingTheStock() {
        for (String type : new String[]{"ENTRY", "ENTRADA", "COMPRA", "AJUSTE", "DEVOLUCION_VENTA"}) {
            reset(productFacade, movementRepository);
            ProductDto product = new ProductDto(1L, "P1", "123", "Product 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.TEN, 10, 5, true, null);
            when(productFacade.getById(1L)).thenReturn(Optional.of(product));
            when(productFacade.adjustStock(1L, 4)).thenReturn(14);
            when(movementRepository.save(any(InventoryMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

            inventoryService.registerMovement(1L, type, 4, "Sube stock");

            // El orden importa: consolidar con el stock de antes de la subida, no con el de después.
            org.mockito.InOrder inOrder = inOrder(productFacade);
            inOrder.verify(productFacade).consolidateLots(1L);
            inOrder.verify(productFacade).adjustStock(1L, 4);
        }
    }

    @Test
    void registerMovement_EveryStockDecrease_DoesNotConsolidate() {
        for (String type : new String[]{"SALIDA", "EXIT", "VENTA", "DEVOLUCION", "RETURN"}) {
            reset(productFacade, movementRepository);
            ProductDto product = new ProductDto(1L, "P1", "123", "Product 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.TEN, 10, 5, true, null);
            when(productFacade.getById(1L)).thenReturn(Optional.of(product));
            when(productFacade.adjustStock(1L, -3)).thenReturn(7);
            when(movementRepository.save(any(InventoryMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

            inventoryService.registerMovement(1L, type, 3, "Baja stock");

            verify(productFacade, never()).consolidateLots(any());
        }
    }

    @Test
    void registerMovement_ExpirationOnAnExit_IsRejectedWithoutTouchingStock() {
        ProductDto product = new ProductDto(1L, "P1", "123", "Product 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.TEN, 10, 5, true, null);
        when(productFacade.getById(1L)).thenReturn(Optional.of(product));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> inventoryService.registerMovement(1L, "SALIDA", 2, "Merma", java.time.LocalDate.of(2027, 1, 15), null));

        assertTrue(ex.getMessage().contains("solo se puede registrar en una entrada"));
        verify(productFacade, never()).adjustStock(any(), anyInt());
        verify(productFacade, never()).addLot(any(), anyInt(), any(), any());
    }

    @Test
    void registerMovement_InvalidLot_FailsBeforeRaisingTheStock() {
        ProductDto product = new ProductDto(1L, "P1", "123", "Product 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.TEN, 10, 5, true, null);
        when(productFacade.getById(1L)).thenReturn(Optional.of(product));
        java.time.LocalDate absurd = java.time.LocalDate.now().plusYears(40);
        doThrow(new IllegalArgumentException("La fecha de vencimiento no puede superar los 20 años a partir de hoy"))
                .when(productFacade).addLot(1L, 5, absurd, null);

        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.registerMovement(1L, "ENTRY", 5, "Cargamento", absurd, null));

        verify(productFacade, never()).adjustStock(any(), anyInt());
        verify(movementRepository, never()).save(any(InventoryMovement.class));
    }

    @Test
    void registerMovement_InsufficientStock_ThrowsException() {
        ProductDto product = new ProductDto(1L, "P1", "123", "Product 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.TEN, 2, 5, true, null);
        when(productFacade.getById(1L)).thenReturn(Optional.of(product));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> inventoryService.registerMovement(1L, "SALIDA", 5, "Merma"));
        assertTrue(ex.getMessage().contains("Inventario insuficiente"));
    }

    @Test
    void registerMovement_StockCrossesBelowMinimum_TriggersLowStockAlert() {
        // 8 unidades disponibles, min 5: una salida de 4 deja el stock en 4 (<= 5), cruzando
        // el minimo desde arriba (8 > 5) hacia abajo.
        ProductDto product = new ProductDto(1L, "P1", "123", "Product 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.TEN, 8, 5, true, null);
        when(productFacade.getById(1L)).thenReturn(Optional.of(product));
        when(movementRepository.save(any(InventoryMovement.class))).thenReturn(new InventoryMovement());
        when(inventoryMapper.toDto(any(InventoryMovement.class)))
                .thenReturn(new InventoryMovementDto(1L, 1L, "Product 1", "SALIDA", 4, 8, 4, "Venta", "admin", null));

        when(productFacade.adjustStock(1L, -4)).thenReturn(4);

        inventoryService.registerMovement(1L, "SALIDA", 4, "Venta");

        verify(lowStockAlertNotifier, times(1)).notifyLowStock(1L, product, 4);
    }

    @Test
    void registerMovement_StockAlreadyBelowMinimum_DoesNotRepeatAlert() {
        // Ya estaba en 4 (<= min 5): otra salida que lo deja en 3 NO debe re-disparar la alerta,
        // solo el cruce inicial.
        ProductDto product = new ProductDto(1L, "P1", "123", "Product 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.TEN, 4, 5, true, null);
        when(productFacade.getById(1L)).thenReturn(Optional.of(product));
        when(movementRepository.save(any(InventoryMovement.class))).thenReturn(new InventoryMovement());
        when(inventoryMapper.toDto(any(InventoryMovement.class)))
                .thenReturn(new InventoryMovementDto(1L, 1L, "Product 1", "SALIDA", 1, 4, 3, "Venta", "admin", null));

        when(productFacade.adjustStock(1L, -1)).thenReturn(3);

        inventoryService.registerMovement(1L, "SALIDA", 1, "Venta");

        verify(lowStockAlertNotifier, never()).notifyLowStock(any(), any(), anyInt());
    }
}
