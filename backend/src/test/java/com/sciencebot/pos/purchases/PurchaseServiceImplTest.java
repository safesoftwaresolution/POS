package com.sciencebot.pos.purchases;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.purchases.internal.entities.Purchase;
import com.sciencebot.pos.purchases.internal.repositories.PurchaseRepository;
import com.sciencebot.pos.purchases.internal.services.PurchaseServiceImpl;
import com.sciencebot.pos.purchases.internal.mappers.PurchaseMapper;
import com.sciencebot.pos.inventory.InventoryFacade;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.suppliers.SupplierDto;
import com.sciencebot.pos.suppliers.SupplierFacade;
import com.sciencebot.pos.users.UserFacade;
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
import static org.mockito.Mockito.*;

class PurchaseServiceImplTest {

    private static final Long STORE_ID = 1L;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private PurchaseMapper purchaseMapper;

    @Mock
    private SupplierFacade supplierFacade;

    @Mock
    private ProductFacade productFacade;

    @Mock
    private InventoryFacade inventoryFacade;

    @Mock
    private UserFacade userFacade;

    @InjectMocks
    private PurchaseServiceImpl purchaseService;

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
    void registerPurchase_Success() {
        CreatePurchaseItemCommand item = new CreatePurchaseItemCommand(1L, 10, BigDecimal.valueOf(1.15));
        CreatePurchaseCommand command = new CreatePurchaseCommand(1L, "FAC-01", List.of(item));

        SupplierDto supplier = new SupplierDto(1L, "Supp ABC", "123", "Pedro", "a@a.com", "123", "Calle", true);
        when(supplierFacade.getById(1L)).thenReturn(Optional.of(supplier));

        ProductDto product = new ProductDto(1L, "P1", "123", "Prod 1", "Cat", null, null, BigDecimal.ONE, BigDecimal.TEN, 5, 2, true, null);
        when(productFacade.getById(1L)).thenReturn(Optional.of(product));

        Purchase saved = new Purchase();
        saved.setId(10L);
        saved.setTotalAmount(BigDecimal.valueOf(11.50));
        when(purchaseRepository.saveAndFlush(any(Purchase.class))).thenReturn(saved);

        PurchaseDto expectedDto = new PurchaseDto(10L, 1L, "Supp ABC", "FAC-01", BigDecimal.valueOf(11.50), "admin", null, List.of());
        when(purchaseMapper.toDto(any(Purchase.class))).thenReturn(expectedDto);

        PurchaseDto result = purchaseService.registerPurchase(command);

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(11.50), result.totalAmount());
        verify(productFacade, times(1)).updatePurchasePrice(1L, BigDecimal.valueOf(1.15));
        verify(inventoryFacade, times(1)).registerMovement(eq(1L), eq("COMPRA"), eq(10), anyString());
        verify(purchaseRepository, times(1)).saveAndFlush(any(Purchase.class));
    }

    @Test
    void registerPurchase_DuplicateIdempotencyKey_ReturnsExistingWithoutSideEffects() {
        CreatePurchaseItemCommand item = new CreatePurchaseItemCommand(1L, 10, BigDecimal.valueOf(1.15));
        CreatePurchaseCommand command = new CreatePurchaseCommand(1L, "FAC-01", List.of(item));

        Purchase existing = new Purchase();
        existing.setId(10L);
        existing.setStoreId(STORE_ID);
        existing.setIdempotencyKey("key-abc");
        when(purchaseRepository.findByIdempotencyKey("key-abc")).thenReturn(Optional.of(existing));

        PurchaseDto existingDto = new PurchaseDto(10L, 1L, "Supp ABC", "FAC-01", BigDecimal.valueOf(11.50), "admin", null, List.of());
        when(purchaseMapper.toDto(existing)).thenReturn(existingDto);

        PurchaseDto result = purchaseService.registerPurchase(command, "key-abc");

        assertNotNull(result);
        assertEquals(10L, result.id());
        // Reenvío idempotente: no incrementa inventario, no actualiza precios ni persiste otra compra.
        verify(inventoryFacade, never()).registerMovement(anyLong(), anyString(), anyInt(), anyString());
        verify(productFacade, never()).updatePurchasePrice(anyLong(), any());
        verify(purchaseRepository, never()).saveAndFlush(any(Purchase.class));
    }

    @Test
    void registerPurchase_InactiveSupplier_ThrowsException() {
        CreatePurchaseItemCommand item = new CreatePurchaseItemCommand(1L, 10, BigDecimal.valueOf(1.15));
        CreatePurchaseCommand command = new CreatePurchaseCommand(1L, "FAC-01", List.of(item));

        SupplierDto supplier = new SupplierDto(1L, "Supp ABC", "123", "Pedro", "a@a.com", "123", "Calle", false); // Inactive!
        when(supplierFacade.getById(1L)).thenReturn(Optional.of(supplier));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
                () -> purchaseService.registerPurchase(command));
        assertTrue(ex.getMessage().contains("proveedores inactivos"));
    }
}
