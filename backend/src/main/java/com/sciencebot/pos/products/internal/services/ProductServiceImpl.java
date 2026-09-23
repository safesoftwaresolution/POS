package com.sciencebot.pos.products.internal.services;

import com.sciencebot.pos.categories.CategoryDeleteValidator;
import com.sciencebot.pos.categories.CategoryFacade;
import com.sciencebot.pos.categories.CategoryProductCountProvider;
import com.sciencebot.pos.categories.SubcategoryDeleteValidator;
import com.sciencebot.pos.categories.SubcategoryFacade;
import com.sciencebot.pos.categories.SubcategoryProductCountProvider;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.products.*;
import com.sciencebot.pos.products.internal.entities.Product;
import com.sciencebot.pos.products.internal.repositories.ProductRepository;
import com.sciencebot.pos.products.internal.repositories.ProductSpecifications;
import com.sciencebot.pos.products.internal.mappers.ProductMapper;
import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductFacade, CategoryDeleteValidator, CategoryProductCountProvider,
        SubcategoryDeleteValidator, SubcategoryProductCountProvider {

    private final ProductRepository productRepository;
    private final CategoryFacade categoryFacade;
    private final SubcategoryFacade subcategoryFacade;
    private final ProductMapper productMapper;

    public ProductServiceImpl(
            ProductRepository productRepository,
            @org.springframework.context.annotation.Lazy CategoryFacade categoryFacade,
            @org.springframework.context.annotation.Lazy SubcategoryFacade subcategoryFacade,
            ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.categoryFacade = categoryFacade;
        this.subcategoryFacade = subcategoryFacade;
        this.productMapper = productMapper;
    }

    @Override
    public Optional<ProductDto> getById(Long id) {
        Product product = productRepository.findById(id)
                .filter(this::belongsToCurrentStore)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado con ID: " + id));
        return Optional.of(productMapper.toDto(product));
    }


    @Override
    @Transactional
    public ProductDto createProduct(CreateProductCommand command) {
        validateCommand(command.internalCode(), command.purchasePrice(), command.salePrice(), command.categoryId());
        Long storeId = requireCurrentStoreId();

        if (productRepository.existsByStoreIdAndInternalCode(storeId, command.internalCode().trim())) {
            throw new IllegalArgumentException("Ya existe un producto con el código interno: " + command.internalCode().trim());
        }

        if (command.barcode() != null && !command.barcode().isBlank()) {
            String cleanBarcode = command.barcode().trim();
            if (productRepository.existsByStoreIdAndBarcode(storeId, cleanBarcode)) {
                throw new IllegalArgumentException("Ya existe un producto con el código de barras: " + cleanBarcode);
            }
        }

        categoryFacade.getById(command.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("La categoría especificada no existe"));

        validateSubcategory(command.subcategoryId(), command.categoryId());
        Product product = new Product();
        product.setStoreId(storeId);
        product.setInternalCode(command.internalCode().trim());
        product.setBarcode(command.barcode() != null ? command.barcode().trim() : null);
        product.setName(command.name().trim());
        product.setCategoryId(command.categoryId());
        product.setSubcategoryId(command.subcategoryId());
        product.setPurchasePrice(command.purchasePrice());
        product.setSalePrice(command.salePrice());
        product.setMinStock(command.minStock());
        product.setDescription(command.description() != null ? command.description().trim() : null);
        product.setImageUrl(command.imageUrl() != null ? command.imageUrl().trim() : null);
        product.setTaxRate(command.taxRate() != null ? command.taxRate() : BigDecimal.ZERO);
        product.setUnspscCode(command.unspscCode() != null ? command.unspscCode().trim() : null);
        product.setQuantityAvailable(0);
        product.setActive(true);

        Product saved = productRepository.save(product);
        return productMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ProductDto updateProduct(Long id, UpdateProductCommand command) {
        Product product = productRepository.findById(id)
                .filter(this::belongsToCurrentStore)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado con ID: " + id));

        validatePrices(command.purchasePrice(), command.salePrice());

        categoryFacade.getById(command.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("La categoría especificada no existe"));

        validateSubcategory(command.subcategoryId(), command.categoryId());

        if (command.barcode() != null && !command.barcode().isBlank()) {
            String cleanBarcode = command.barcode().trim();
            if (!cleanBarcode.equals(product.getBarcode())
                    && productRepository.existsByStoreIdAndBarcode(product.getStoreId(), cleanBarcode)) {
                throw new IllegalArgumentException("Ya existe un producto con el código de barras: " + cleanBarcode);
            }
        }

        product.setName(command.name().trim());
        product.setCategoryId(command.categoryId());
        product.setSubcategoryId(command.subcategoryId());
        product.setPurchasePrice(command.purchasePrice());
        product.setSalePrice(command.salePrice());
        product.setMinStock(command.minStock());
        product.setBarcode(command.barcode() != null ? command.barcode().trim() : null);
        product.setDescription(command.description() != null ? command.description().trim() : null);
        product.setImageUrl(command.imageUrl() != null ? command.imageUrl().trim() : null);
        if (command.taxRate() != null) {
            product.setTaxRate(command.taxRate());
        }
        if (command.unspscCode() != null) {
            product.setUnspscCode(command.unspscCode().trim());
        }

        Product saved = productRepository.save(product);
        return productMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, boolean active) {
        Product product = productRepository.findById(id)
                .filter(this::belongsToCurrentStore)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado con ID: " + id));
        product.setActive(active);
        productRepository.save(product);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .filter(this::belongsToCurrentStore)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado con ID: " + id));

        product.setActive(false);
        productRepository.save(product);
    }

    @Override
    public Page<ProductDto> searchProducts(String search, Long categoryId, Boolean active, Pageable pageable) {
        Specification<Product> spec = Specification.unrestricted();
        if (search != null) {
            spec = spec.and(ProductSpecifications.hasName(search));
            spec = spec.or(ProductSpecifications.hasInternalCode(search));
            spec = spec.or(ProductSpecifications.hasBarcode(search));
        }
        if (categoryId != null) {
            spec = spec.and(ProductSpecifications.hasCategoryId(categoryId));
        }
        if (active != null) {
            spec = spec.and(ProductSpecifications.hasActive(active));
        }
        // Se agrega al final: and() envuelve TODO lo acumulado como una unidad, asi que esto
        // aplica el filtro de local sin importar el arbol OR construido arriba para 'search'.
        spec = spec.and(ProductSpecifications.hasStoreId(requireCurrentStoreId()));

        Page<Product> page = productRepository.findAll(spec, pageable);
        return page.map(productMapper::toDto);

    }

    /** Un producto de otro local se trata como inexistente (evita filtrar su existencia). */
    private boolean belongsToCurrentStore(Product product) {
        return product.getStoreId().equals(TenantContext.getStoreId());
    }

    private static Long requireCurrentStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeId;
    }

    @Override
    public void validateBeforeDelete(Long categoryId) {
        if (productRepository.existsByCategoryIdAndActiveTrue(categoryId)) {
            throw new IllegalStateException("No se puede eliminar la categoría porque tiene productos asociados activos");
        }
    }

    @Override
    public int getProductCount(Long categoryId) {
        return productRepository.countByCategoryIdAndActiveTrue(categoryId);
    }

    @Override
    public void validateSubcategoryBeforeDelete(Long subcategoryId) {
        if (productRepository.existsBySubcategoryIdAndActiveTrue(subcategoryId)) {
            throw new IllegalStateException("No se puede eliminar la subcategoría porque tiene productos asociados activos");
        }
    }

    @Override
    public int getSubcategoryProductCount(Long subcategoryId) {
        return productRepository.countBySubcategoryIdAndActiveTrue(subcategoryId);
    }

    private void validateSubcategory(Long subcategoryId, Long categoryId) {
        if (subcategoryId == null) {
            return;
        }
        var subcategory = subcategoryFacade.getById(subcategoryId)
                .orElseThrow(() -> new IllegalArgumentException("La subcategoría especificada no existe"));
        if (!subcategory.categoryId().equals(categoryId)) {
            throw new IllegalArgumentException("La subcategoría seleccionada no pertenece a la categoría elegida");
        }
    }


    private void validateCommand(String internalCode, BigDecimal purchasePrice, BigDecimal salePrice, Long categoryId) {
        if (internalCode == null || internalCode.isBlank()) {
            throw new IllegalArgumentException("El código interno es obligatorio");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("La categoría es obligatoria");
        }
        validatePrices(purchasePrice, salePrice);
    }

    private void validatePrices(BigDecimal purchasePrice, BigDecimal salePrice) {
        if (purchasePrice == null || purchasePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio de compra no puede ser negativo");
        }
        if (salePrice == null || salePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio de venta no puede ser negativo");
        }
        if (salePrice.compareTo(purchasePrice) < 0) {
            throw new IllegalArgumentException("El precio de venta debe ser mayor o igual al precio de compra (RN-PROD-001)");
        }
    }

    @Override
    @Transactional
    public void updateStock(Long id, int quantityDelta) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Producto no encontrado con ID: " + id));
        int newStock = product.getQuantityAvailable() + quantityDelta;
        if (newStock < 0) {
            throw new IllegalArgumentException("No hay suficiente stock para el producto: " + product.getName());
        }
        product.setQuantityAvailable(newStock);
        productRepository.save(product);
    }

    @Override
    @Transactional
    public void updatePurchasePrice(Long id, BigDecimal newPrice) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Producto no encontrado con ID: " + id));
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio de compra no puede ser negativo");
        }
        if (product.getSalePrice().compareTo(newPrice) < 0) {
            throw new IllegalArgumentException("El nuevo precio de compra no puede ser mayor que el precio de venta actual: " + product.getSalePrice());
        }
        product.setPurchasePrice(newPrice);
        productRepository.save(product);
    }
}

