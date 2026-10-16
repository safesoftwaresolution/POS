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
