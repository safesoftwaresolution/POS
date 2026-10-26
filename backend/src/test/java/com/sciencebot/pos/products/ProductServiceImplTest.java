package com.sciencebot.pos.products;

import com.sciencebot.pos.categories.CategoryDto;
import com.sciencebot.pos.categories.CategoryFacade;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.products.internal.entities.Product;
import com.sciencebot.pos.products.internal.entities.ProductLot;
import com.sciencebot.pos.products.internal.repositories.ProductLotRepository;
import com.sciencebot.pos.products.internal.repositories.ProductRepository;
import com.sciencebot.pos.products.internal.services.ProductServiceImpl;
import com.sciencebot.pos.products.internal.mappers.ProductMapper;
import com.sciencebot.pos.storage.StorageFacade;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceImplTest {

    private static final Long STORE_ID = 1L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductLotRepository productLotRepository;

    @Mock
    private CategoryFacade categoryFacade;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private StorageFacade storageFacade;

    @InjectMocks
    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setStoreId(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }


    @Test
    void createProduct_Success() {
        CreateProductCommand command = new CreateProductCommand(
                "PROD-001", "7701234567890", "Coca Cola 350ml", 1L, null,
                BigDecimal.valueOf(1.20), BigDecimal.valueOf(1.80), 10, 0, "Coca Cola", null,
                BigDecimal.ZERO, null
        );

        when(categoryFacade.getById(1L)).thenReturn(Optional.of(new CategoryDto(1L, "Bebidas", "Bebidas", 0)));
        when(productRepository.existsByStoreIdAndInternalCode(STORE_ID, "PROD-001")).thenReturn(false);
        when(productRepository.existsByStoreIdAndBarcode(STORE_ID, "7701234567890")).thenReturn(false);

        Product saved = new Product();
        saved.setId(10L);
        saved.setStoreId(STORE_ID);
        saved.setInternalCode("PROD-001");
        saved.setBarcode("7701234567890");
        saved.setName("Coca Cola 350ml");
        saved.setCategoryId(1L);
        saved.setPurchasePrice(BigDecimal.valueOf(1.20));
        saved.setSalePrice(BigDecimal.valueOf(1.80));
        saved.setActive(true);

        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductDto expectedDto = new ProductDto(
                10L, "PROD-001", "7701234567890", "Coca Cola 350ml", "Bebidas", null, null,
                BigDecimal.valueOf(1.20), BigDecimal.valueOf(1.80), 0, 10, true, null
        );
        when(productMapper.toDto(any(Product.class), any())).thenReturn(expectedDto);

        ProductDto result = productService.createProduct(command);


        assertNotNull(result);
        assertEquals(10L, result.id());
        assertEquals("PROD-001", result.internalCode());
        assertEquals("Bebidas", result.categoryName());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void createProduct_WithInitialStock_SetsQuantityAvailable() {
        CreateProductCommand command = new CreateProductCommand(
                "PROD-001", "7701234567890", "Coca Cola 350ml", 1L, null,
                BigDecimal.valueOf(1.20), BigDecimal.valueOf(1.80), 10, 50, "Coca Cola", null,
                BigDecimal.ZERO, null
        );

        when(categoryFacade.getById(1L)).thenReturn(Optional.of(new CategoryDto(1L, "Bebidas", "Bebidas", 0)));
        when(productRepository.existsByStoreIdAndInternalCode(STORE_ID, "PROD-001")).thenReturn(false);
        when(productRepository.existsByStoreIdAndBarcode(STORE_ID, "7701234567890")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productMapper.toDto(any(Product.class), any())).thenReturn(
                new ProductDto(10L, "PROD-001", "7701234567890", "Coca Cola 350ml", "Bebidas", null, null,
                        BigDecimal.valueOf(1.20), BigDecimal.valueOf(1.80), 50, 10, true, null)
        );

        productService.createProduct(command);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals(50, captor.getValue().getQuantityAvailable());
    }

    @Test
    void createProduct_NegativeInitialStock_ThrowsException() {
        CreateProductCommand command = new CreateProductCommand(
                "PROD-001", "7701234567890", "Coca Cola 350ml", 1L, null,
                BigDecimal.valueOf(1.20), BigDecimal.valueOf(1.80), 10, -5, "Coca Cola", null,
                BigDecimal.ZERO, null
        );

        when(categoryFacade.getById(1L)).thenReturn(Optional.of(new CategoryDto(1L, "Bebidas", "Bebidas", 0)));
        when(productRepository.existsByStoreIdAndInternalCode(STORE_ID, "PROD-001")).thenReturn(false);
        when(productRepository.existsByStoreIdAndBarcode(STORE_ID, "7701234567890")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> productService.createProduct(command));
        assertTrue(ex.getMessage().contains("stock inicial"));
    }

    @Test
    void createProduct_InvalidMargin_ThrowsException() {
        CreateProductCommand command = new CreateProductCommand(
                "PROD-001", "7701234567890", "Coca Cola 350ml", 1L, null,
                BigDecimal.valueOf(1.80), BigDecimal.valueOf(1.20), 10, 0, "Coca Cola", null,
                BigDecimal.ZERO, null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> productService.createProduct(command));
        assertTrue(ex.getMessage().contains("precio de venta debe ser mayor"));
    }

    @Test
    void validateBeforeDelete_ProductsExist_ThrowsException() {
        when(productRepository.existsByCategoryIdAndActiveTrue(1L)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> productService.validateBeforeDelete(1L));
        assertTrue(ex.getMessage().contains("productos asociados"));
    }

    @Test
    void validateBeforeDelete_NoProductsExist_Succeeds() {
        when(productRepository.existsByCategoryIdAndActiveTrue(1L)).thenReturn(false);

        assertDoesNotThrow(() -> productService.validateBeforeDelete(1L));
    }

    // ---- Vencimiento del stock inicial al crear ----

    @Test
    void createProduct_WithExpirationDate_CreatesALotForTheInitialStock() {
        LocalDate expiration = LocalDate.now().plusDays(20);
        stubCreate();

        productService.createProduct(createCommand(12, expiration));

        ArgumentCaptor<ProductLot> captor = ArgumentCaptor.forClass(ProductLot.class);
        verify(productLotRepository).save(captor.capture());
        assertEquals(12, captor.getValue().getQuantity());
        assertEquals(expiration, captor.getValue().getExpirationDate());
        assertEquals(STORE_ID, captor.getValue().getStoreId());
    }

    @Test
    void createProduct_WithoutExpirationDate_CreatesNoLot() {
        stubCreate();

        productService.createProduct(createCommand(12, null));

        verify(productLotRepository, never()).save(any(ProductLot.class));
    }

    @Test
    void createProduct_ExpirationWithoutInitialStock_ThrowsException() {
        stubCreate();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> productService.createProduct(createCommand(0, LocalDate.now().plusDays(5))));

        assertTrue(ex.getMessage().contains("stock inicial"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_ExpirationMoreThanTwentyYearsAhead_ThrowsException() {
        stubCreate();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> productService.createProduct(createCommand(5, LocalDate.now().plusYears(21))));

        assertTrue(ex.getMessage().contains("fecha de vencimiento"));
        verify(productRepository, never()).save(any(Product.class));
    }

    // ---- Lotes ----

    @Test
    void getById_ExposesTheExpirationOfTheNearestLotThatStillHasUnits() {
        // Lotes de 10 (nov) y 50 (ene) = 60; stock 50: se vendieron 10 y salen del lote que vence primero.
        Product product = ownProduct(50);
        stubMapper();
        LocalDate january = LocalDate.of(2027, 1, 15);
        when(productLotRepository.findByStoreIdAndProductIdOrderByExpirationDateAscIdAsc(STORE_ID, 10L))
                .thenReturn(List.of(lot(1L, 10, LocalDate.of(2026, 11, 10)), lot(2L, 50, january)));

        productService.getById(10L);

        verify(productMapper).toDto(product, january);
    }

    @Test
    void getById_WithoutLots_HasNoExpiration() {
        Product product = ownProduct(50);
        stubMapper();
        when(productLotRepository.findByStoreIdAndProductIdOrderByExpirationDateAscIdAsc(STORE_ID, 10L)).thenReturn(List.of());

        productService.getById(10L);

        verify(productMapper).toDto(product, null);
    }

    @Test
    void addLot_SavesALotInTheProductsStore() {
        ownProduct(20);
        LocalDate expiration = LocalDate.now().plusDays(40);

        productService.addLot(10L, 8, expiration, "  L-77  ");

        ArgumentCaptor<ProductLot> captor = ArgumentCaptor.forClass(ProductLot.class);
        verify(productLotRepository).save(captor.capture());
        assertEquals(10L, captor.getValue().getProductId());
        assertEquals(STORE_ID, captor.getValue().getStoreId());
        assertEquals(8, captor.getValue().getQuantity());
        assertEquals(expiration, captor.getValue().getExpirationDate());
        assertEquals("L-77", captor.getValue().getLotCode());
    }

    @Test
    void addLot_AlreadyExpiredDate_IsAccepted() {
        ownProduct(20);

        assertDoesNotThrow(() -> productService.addLot(10L, 3, LocalDate.now().minusDays(2), null));
    }

    @Test
    void addLot_InvalidData_ThrowsException() {
        ownProduct(20);
        LocalDate ok = LocalDate.now().plusDays(10);

        assertThrows(IllegalArgumentException.class, () -> productService.addLot(10L, 0, ok, null));
        assertThrows(IllegalArgumentException.class, () -> productService.addLot(10L, 5, null, null));
        assertThrows(IllegalArgumentException.class, () -> productService.addLot(10L, 5, LocalDate.now().plusYears(21), null));
        assertThrows(IllegalArgumentException.class, () -> productService.addLot(10L, 5, ok, "x".repeat(51)));
        verify(productLotRepository, never()).save(any(ProductLot.class));
    }

    @Test
    void addLot_ProductOfAnotherStore_IsNotFound() {
        Product other = new Product();
        other.setId(10L);
        other.setStoreId(99L);
        when(productRepository.findById(10L)).thenReturn(Optional.of(other));

        assertThrows(EntityNotFoundException.class,
                () -> productService.addLot(10L, 5, LocalDate.now().plusDays(10), null));
        verify(productLotRepository, never()).save(any(ProductLot.class));
    }

    @Test
    void listLots_ReturnsOnlyLotsWithUnitsAndTheUndatedQuantity() {
        ownProduct(0);
        // Lotes 10 + 50 = 60 y stock 65: no se consumió nada y sobran 5 unidades sin fecha.
        when(productRepository.findStockById(10L)).thenReturn(65);
        when(productLotRepository.findByStoreIdAndProductIdOrderByExpirationDateAscIdAsc(STORE_ID, 10L))
                .thenReturn(List.of(lot(1L, 10, LocalDate.of(2026, 11, 10)), lot(2L, 50, LocalDate.of(2027, 1, 15))));

        ProductLotsDto result = productService.listLots(10L);

        assertEquals(5, result.undatedQuantity());
        assertEquals(List.of(10, 50), result.lots().stream().map(ProductLotDto::remainingQuantity).toList());

        // Con stock 30 se vendieron 30: el lote de 10 se agota y el de 50 queda en 30.
        when(productRepository.findStockById(10L)).thenReturn(30);
        ProductLotsDto afterSales = productService.listLots(10L);

        assertEquals(0, afterSales.undatedQuantity());
        assertEquals(List.of(2L), afterSales.lots().stream().map(ProductLotDto::id).toList());
        assertEquals(30, afterSales.lots().get(0).remainingQuantity());
    }

    @Test
    void assignLot_MoreThanTheUndatedUnits_ThrowsException() {
        ownProduct(0);
        when(productRepository.findStockById(10L)).thenReturn(15);
        when(productLotRepository.findByStoreIdAndProductIdOrderByExpirationDateAscIdAsc(STORE_ID, 10L))
                .thenReturn(List.of(lot(1L, 10, LocalDate.of(2026, 11, 10)))); // 5 unidades sin fecha

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> productService.assignLot(10L, 6, LocalDate.now().plusDays(30), null));

        assertTrue(ex.getMessage().contains("5 unidades sin fecha"));
        verify(productLotRepository, never()).save(any(ProductLot.class));
    }

    @Test
    void assignLot_WithoutUndatedUnits_ThrowsException() {
        ownProduct(0);
        when(productRepository.findStockById(10L)).thenReturn(10);
        when(productLotRepository.findByStoreIdAndProductIdOrderByExpirationDateAscIdAsc(STORE_ID, 10L))
                .thenReturn(List.of(lot(1L, 10, LocalDate.of(2026, 11, 10))));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> productService.assignLot(10L, 1, LocalDate.now().plusDays(30), null));

        assertTrue(ex.getMessage().contains("no tiene unidades sin fecha"));
    }

    @Test
    void assignLot_WithinTheUndatedUnits_SavesTheLotWithoutMovingStock() {
        ownProduct(0);
        when(productRepository.findStockById(10L)).thenReturn(40); // sin lotes: las 40 están sin fecha
        when(productLotRepository.findByStoreIdAndProductIdOrderByExpirationDateAscIdAsc(STORE_ID, 10L)).thenReturn(List.of());
        LocalDate expiration = LocalDate.now().plusDays(60);

        productService.assignLot(10L, 40, expiration, "L-1");

        ArgumentCaptor<ProductLot> captor = ArgumentCaptor.forClass(ProductLot.class);
        verify(productLotRepository).save(captor.capture());
        assertEquals(40, captor.getValue().getQuantity());
        assertEquals(expiration, captor.getValue().getExpirationDate());
        verify(productRepository, never()).adjustStockInStore(any(), any(), anyInt());
    }

    @Test
    void updateLot_ChangesTheDateAndTheCode_AndKeepsTheUnitsWhenNoneAreSent() {
        ProductLot lot = lot(7L, 10, LocalDate.of(2026, 11, 10));
        withLots(10, lot);
        LocalDate corrected = LocalDate.now().plusDays(90);

        productService.updateLot(10L, 7L, corrected, "  ", null);

        assertEquals(corrected, lot.getExpirationDate());
        assertNull(lot.getLotCode(), "un código en blanco lo borra");
        assertEquals(10, lot.getQuantity(), "sin cantidad las unidades no cambian");
        verify(productLotRepository).save(lot);
    }

    @Test
    void updateLot_UnknownLot_IsNotFound() {
        withLots(10, lot(7L, 10, LocalDate.of(2026, 11, 10)));

        assertThrows(EntityNotFoundException.class,
                () -> productService.updateLot(10L, 99L, LocalDate.now().plusDays(5), null, null));
    }

    @Test
    void updateLot_LowerUnits_ReleasesThemToTheUndatedUnits() {
        ProductLot lot = lot(7L, 50, LocalDate.of(2027, 1, 15));
        withLots(50, lot);

        productService.updateLot(10L, 7L, lot.getExpirationDate(), null, 40);

        assertEquals(40, lot.getQuantity());
    }

    @Test
    void updateLot_RaiseUnitsWithinTheUndatedUnits_Succeeds() {
        ProductLot lot = lot(7L, 40, LocalDate.of(2027, 1, 15));
        withLots(50, lot); // 10 unidades sin fecha

        productService.updateLot(10L, 7L, lot.getExpirationDate(), null, 50);

        assertEquals(50, lot.getQuantity());
    }

    @Test
    void updateLot_RaiseUnitsBeyondTheUndatedUnits_ThrowsAndChangesNothing() {
        ProductLot lot = lot(7L, 40, LocalDate.of(2027, 1, 15));
        withLots(45, lot); // solo 5 unidades sin fecha

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> productService.updateLot(10L, 7L, lot.getExpirationDate(), null, 50));

        assertTrue(ex.getMessage().contains("Solo hay 5 unidades sin fecha"));
        assertEquals(40, lot.getQuantity());
    }

    @Test
    void updateLot_RaiseUnitsWithNoUndatedUnits_ExplainsToRaiseTheStockFirst() {
        ProductLot lot = lot(7L, 40, LocalDate.of(2027, 1, 15));
        withLots(40, lot);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> productService.updateLot(10L, 7L, lot.getExpirationDate(), null, 41));

        assertTrue(ex.getMessage().contains("No hay unidades sin fecha"));
    }

    @Test
    void updateLot_ZeroOrNegativeUnits_Throws() {
        ProductLot lot = lot(7L, 10, LocalDate.of(2027, 1, 15));
        withLots(10, lot);

        assertThrows(IllegalArgumentException.class, () -> productService.updateLot(10L, 7L, lot.getExpirationDate(), null, 0));
        assertThrows(IllegalArgumentException.class, () -> productService.updateLot(10L, 7L, lot.getExpirationDate(), null, -3));
        assertEquals(10, lot.getQuantity());
    }

    @Test
    void updateLot_FixesWhatWasAlreadySoldBeforeEditing() {
        // Lotes de 10 (nov) y 50 (ene), stock 40: se vendieron 20 -> el de nov se agotó y el de ene quedó en 40.
        ProductLot november = lot(1L, 10, LocalDate.of(2026, 11, 10));
        ProductLot january = lot(2L, 50, LocalDate.of(2027, 1, 15));
        withLots(40, november, january);

        productService.updateLot(10L, 2L, LocalDate.of(2027, 2, 1), "E", null);

        verify(productLotRepository).delete(november);
        assertEquals(40, january.getQuantity(), "las unidades del lote pasan a ser las que realmente le quedan");
        assertEquals(LocalDate.of(2027, 2, 1), january.getExpirationDate());
    }

    @Test
    void updateLot_ALotThatWasAlreadyExhausted_IsNotFound() {
        ProductLot sold = lot(1L, 10, LocalDate.of(2026, 11, 10));
        withLots(0, sold);

        assertThrows(EntityNotFoundException.class,
                () -> productService.updateLot(10L, 1L, LocalDate.now().plusDays(5), null, null));
        verify(productLotRepository).delete(sold);
    }

    @Test
    void deleteLot_RemovesTheLotAndLeavesTheStockAlone() {
        ProductLot lot = lot(7L, 10, LocalDate.of(2026, 11, 10));
        withLots(10, lot);

        productService.deleteLot(10L, 7L);

        verify(productLotRepository).delete(lot);
        verify(productRepository, never()).adjustStockInStore(any(), any(), anyInt());
    }

    @Test
    void deleteLot_DoesNotReviveALotThatWasAlreadySold() {
        // El lote de nov (10) se vendió; el de ene (50) tiene 40. Al quitar el de ene, el de nov no debe reaparecer.
        ProductLot november = lot(1L, 10, LocalDate.of(2026, 11, 10));
        ProductLot january = lot(2L, 50, LocalDate.of(2027, 1, 15));
        withLots(40, november, january);

        productService.deleteLot(10L, 2L);

        verify(productLotRepository).delete(november);
        verify(productLotRepository).delete(january);
    }

    @Test
    void deleteLot_UnknownLot_IsNotFound() {
        withLots(10, lot(7L, 10, LocalDate.of(2026, 11, 10)));

        assertThrows(EntityNotFoundException.class, () -> productService.deleteLot(10L, 99L));
    }

    @Test
    void consolidateLots_DeletesExhaustedLotsAndShrinksThePartialOnes() {
        ProductLot november = lot(1L, 10, LocalDate.of(2026, 11, 10));
        ProductLot january = lot(2L, 50, LocalDate.of(2027, 1, 15));
        ProductLot march = lot(3L, 30, LocalDate.of(2027, 3, 1));
        withLots(65, november, january, march); // se vendieron 25: agota nov y deja 35 en ene

        productService.consolidateLots(10L);

        verify(productLotRepository).delete(november);
        assertEquals(35, january.getQuantity());
        verify(productLotRepository).save(january);
        assertEquals(30, march.getQuantity());
        verify(productLotRepository, never()).delete(january);
        verify(productLotRepository, never()).delete(march);
        verify(productLotRepository, never()).save(march);
    }

    @Test
    void consolidateLots_WithStockAboveTheLots_ChangesNothing() {
        ProductLot lot = lot(1L, 10, LocalDate.of(2026, 11, 10));
        withLots(17, lot); // 7 unidades sin fecha

        productService.consolidateLots(10L);

        assertEquals(10, lot.getQuantity());
        verify(productLotRepository, never()).delete(any(ProductLot.class));
        verify(productLotRepository, never()).save(any(ProductLot.class));
    }

    @Test
    void consolidateLots_WithoutLots_DoesNothing() {
        withLots(10);

        productService.consolidateLots(10L);

        verify(productLotRepository, never()).delete(any(ProductLot.class));
        verify(productLotRepository, never()).save(any(ProductLot.class));
    }

    @Test
    void assignLot_IgnoresLotsThatWereAlreadySold() {
        // Lotes de 10 (nov, vendido) y 30 (ene); stock 30: no quedan unidades sin fecha aunque la suma de lotes sea 40.
        withLots(30, lot(1L, 10, LocalDate.of(2026, 11, 10)), lot(2L, 30, LocalDate.of(2027, 1, 15)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> productService.assignLot(10L, 1, LocalDate.now().plusDays(30), null));

        assertTrue(ex.getMessage().contains("no tiene unidades sin fecha"));
    }

    // ---- foto del producto en R2 ----

    private static final String OLD_URL = "https://pub.r2.dev/tienda-1/imagenes-productos/vieja.jpg";
    private static final String NEW_URL = "https://pub.r2.dev/tienda-1/imagenes-productos/nueva.jpg";
    private static final String PREFIX = "tienda-1/imagenes-productos/";

    @Test
    void updateProduct_ReplacingThePhoto_DeletesTheOldOneFromR2() {
        stubUpdate(OLD_URL);

        productService.updateProduct(10L, updateCommand(NEW_URL));

        verify(storageFacade).deleteFileByUrl(OLD_URL, PREFIX);
    }

    @Test
    void updateProduct_RemovingThePhoto_DeletesTheOldOneFromR2() {
        stubUpdate(OLD_URL);

        productService.updateProduct(10L, updateCommand(null));

        verify(storageFacade).deleteFileByUrl(OLD_URL, PREFIX);
    }

    @Test
    void updateProduct_KeepingThePhoto_DeletesNothing() {
        stubUpdate(OLD_URL);

        productService.updateProduct(10L, updateCommand(OLD_URL));

        verifyNoInteractions(storageFacade);
    }

    @Test
    void updateProduct_OldPhotoStillUsedByAnotherProduct_IsKept() {
        stubUpdate(OLD_URL);
        when(productRepository.existsByStoreIdAndImageUrl(STORE_ID, OLD_URL)).thenReturn(true);

        productService.updateProduct(10L, updateCommand(NEW_URL));

        verifyNoInteractions(storageFacade);
    }

    @Test
    void updateProduct_R2Failure_DoesNotBreakTheSave() {
        stubUpdate(OLD_URL);
        doThrow(new IllegalStateException("R2 caído")).when(storageFacade).deleteFileByUrl(any(), any());

        assertDoesNotThrow(() -> productService.updateProduct(10L, updateCommand(NEW_URL)));
    }

    @Test
    void deleteProduct_OnlyDeactivates_AndKeepsThePhoto() {
        Product product = ownProduct(5);
        product.setImageUrl(OLD_URL);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        productService.deleteProduct(10L);

        assertFalse(product.isActive());
        verifyNoInteractions(storageFacade);
    }

    private void stubUpdate(String currentImageUrl) {
        Product product = ownProduct(5);
        product.setImageUrl(currentImageUrl);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(categoryFacade.getById(1L)).thenReturn(Optional.of(new CategoryDto(1L, "Lácteos", "Lácteos", 0)));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubMapper();
    }

    private static UpdateProductCommand updateCommand(String imageUrl) {
        return new UpdateProductCommand("Leche entera 1L", null, 1L, null,
                BigDecimal.valueOf(2000), BigDecimal.valueOf(3000), null, 5, imageUrl);
    }

    // ---- helpers ----

    private void stubMapper() {
        when(productMapper.toDto(any(Product.class), any())).thenReturn(
                new ProductDto(10L, "PROD-002", null, "Leche entera 1L", "Lácteos", null, null,
                        BigDecimal.valueOf(2000), BigDecimal.valueOf(3000), 50, 5, true, null));
    }

    private void stubCreate() {
        when(categoryFacade.getById(1L)).thenReturn(Optional.of(new CategoryDto(1L, "Lácteos", "Lácteos", 0)));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(10L);
            return p;
        });
        when(productLotRepository.save(any(ProductLot.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static CreateProductCommand createCommand(int initialStock, LocalDate expirationDate) {
        return new CreateProductCommand(
                "PROD-002", null, "Leche entera 1L", 1L, null,
                BigDecimal.valueOf(2000), BigDecimal.valueOf(3000), 5, initialStock, "Leche", null,
                BigDecimal.ZERO, null, expirationDate
        );
    }

    /** Producto de este local con el stock dado en la entidad (la usa getById para repartir los lotes). */
    private Product ownProduct(int stock) {
        Product product = new Product();
        product.setId(10L);
        product.setStoreId(STORE_ID);
        product.setInternalCode("PROD-002");
        product.setName("Leche entera 1L");
        product.setCategoryId(1L);
        product.setQuantityAvailable(stock);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productLotRepository.save(any(ProductLot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return product;
    }

    /** Producto de este local con el stock dado en la BD y estos lotes (en orden de vencimiento). */
    private void withLots(int stock, ProductLot... lots) {
        ownProduct(stock);
        when(productRepository.findStockById(10L)).thenReturn(stock);
        when(productLotRepository.findByStoreIdAndProductIdOrderByExpirationDateAscIdAsc(STORE_ID, 10L)).thenReturn(List.of(lots));
    }

    private static ProductLot lot(Long id, int quantity, LocalDate expiration) {
        ProductLot lot = new ProductLot();
        lot.setId(id);
        lot.setProductId(10L);
        lot.setStoreId(STORE_ID);
        lot.setQuantity(quantity);
        lot.setExpirationDate(expiration);
        return lot;
    }
}
