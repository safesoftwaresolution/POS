package com.sciencebot.pos.products;

import com.sciencebot.pos.categories.CategoryDto;
import com.sciencebot.pos.categories.CategoryFacade;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.products.internal.entities.Product;
import com.sciencebot.pos.products.internal.repositories.ProductRepository;
import com.sciencebot.pos.products.internal.services.ProductServiceImpl;
import com.sciencebot.pos.products.internal.mappers.ProductMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceImplTest {

    private static final Long STORE_ID = 1L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryFacade categoryFacade;

    @Mock
    private ProductMapper productMapper;

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
<<<<<<< HEAD
                "PROD-001", "7701234567890", "Coca Cola 350ml", 1L,
                BigDecimal.valueOf(1.20), BigDecimal.valueOf(1.80), 10, 0, "Coca Cola", null
=======
                "PROD-001", "7701234567890", "Coca Cola 350ml", 1L, null,
                BigDecimal.valueOf(1.20), BigDecimal.valueOf(1.80), 10, "Coca Cola", null
>>>>>>> develop
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
        when(productMapper.toDto(any(Product.class))).thenReturn(expectedDto);

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
                "PROD-001", "7701234567890", "Coca Cola 350ml", 1L,
                BigDecimal.valueOf(1.20), BigDecimal.valueOf(1.80), 10, 50, "Coca Cola", null
        );

        when(categoryFacade.getById(1L)).thenReturn(Optional.of(new CategoryDto(1L, "Bebidas", "Bebidas", 0)));
        when(productRepository.existsByInternalCode("PROD-001")).thenReturn(false);
        when(productRepository.existsByBarcode("7701234567890")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productMapper.toDto(any(Product.class))).thenReturn(
                new ProductDto(10L, "PROD-001", "7701234567890", "Coca Cola 350ml", "Bebidas",
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
                "PROD-001", "7701234567890", "Coca Cola 350ml", 1L,
                BigDecimal.valueOf(1.20), BigDecimal.valueOf(1.80), 10, -5, "Coca Cola", null
        );

        when(categoryFacade.getById(1L)).thenReturn(Optional.of(new CategoryDto(1L, "Bebidas", "Bebidas", 0)));
        when(productRepository.existsByInternalCode("PROD-001")).thenReturn(false);
        when(productRepository.existsByBarcode("7701234567890")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> productService.createProduct(command));
        assertTrue(ex.getMessage().contains("stock inicial"));
    }

    @Test
    void createProduct_InvalidMargin_ThrowsException() {
        CreateProductCommand command = new CreateProductCommand(
<<<<<<< HEAD
                "PROD-001", "7701234567890", "Coca Cola 350ml", 1L,
                BigDecimal.valueOf(1.80), BigDecimal.valueOf(1.20), 10, 0, "Coca Cola", null
=======
                "PROD-001", "7701234567890", "Coca Cola 350ml", 1L, null,
                BigDecimal.valueOf(1.80), BigDecimal.valueOf(1.20), 10, "Coca Cola", null
>>>>>>> develop
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
}
