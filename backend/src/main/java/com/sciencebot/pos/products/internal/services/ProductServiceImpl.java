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
import com.sciencebot.pos.products.internal.entities.ProductLot;
import com.sciencebot.pos.products.internal.repositories.ProductLotRepository;
import com.sciencebot.pos.products.internal.repositories.ProductRepository;
import com.sciencebot.pos.products.internal.repositories.ProductSpecifications;
import com.sciencebot.pos.products.internal.mappers.ProductMapper;
import com.sciencebot.pos.storage.StorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductFacade, CategoryDeleteValidator, CategoryProductCountProvider,
        SubcategoryDeleteValidator, SubcategoryProductCountProvider {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private static final int LOOKUP_BATCH_SIZE = 500;
    /** Tope de una fecha de vencimiento: más allá es casi seguro un error de digitación (p. ej. 2206). */
    private static final int MAX_EXPIRATION_YEARS_AHEAD = 20;
    private static final int MAX_LOT_CODE_LENGTH = 50;

    private final ProductRepository productRepository;
    private final ProductLotRepository productLotRepository;
    private final CategoryFacade categoryFacade;
    private final SubcategoryFacade subcategoryFacade;
    private final ProductMapper productMapper;
    private final StorageFacade storageFacade;

    public ProductServiceImpl(
            ProductRepository productRepository,
            ProductLotRepository productLotRepository,
            @org.springframework.context.annotation.Lazy CategoryFacade categoryFacade,
            @org.springframework.context.annotation.Lazy SubcategoryFacade subcategoryFacade,
            ProductMapper productMapper,
            StorageFacade storageFacade) {
        this.productRepository = productRepository;
        this.productLotRepository = productLotRepository;
        this.storageFacade = storageFacade;
        this.categoryFacade = categoryFacade;
        this.subcategoryFacade = subcategoryFacade;
        this.productMapper = productMapper;
    }

    @Override
    public Optional<ProductDto> getById(Long id) {
        Product product = requireOwnProduct(id);
        return Optional.of(toDto(product));
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

        if (command.quantityAvailable() < 0) {
            throw new IllegalArgumentException("El stock inicial no puede ser negativo");
        }

        validateSubcategory(command.subcategoryId(), command.categoryId());
        if (command.expirationDate() != null) {
            if (command.quantityAvailable() <= 0) {
                throw new IllegalArgumentException("El vencimiento del stock inicial requiere un stock inicial mayor que cero");
            }
            validateExpirationDate(command.expirationDate());
        }

        Product product = new Product();
        product.setStoreId(storeId);
        product.setInternalCode(command.internalCode().trim());
        product.setBarcode(normalizeBarcode(command.barcode()));
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
        product.setQuantityAvailable(command.quantityAvailable());
        product.setActive(true);

        Product saved = productRepository.save(product);
        if (command.expirationDate() != null) {
            // El stock inicial entra con su vencimiento: un solo lote con todas las unidades.
            saveLot(saved, command.quantityAvailable(), command.expirationDate(), null);
        }
        return toDto(saved);
    }

    @Override
    @Transactional
    public ProductDto updateProduct(Long id, UpdateProductCommand command) {
        Product product = requireOwnProduct(id);

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

        String previousImageUrl = product.getImageUrl();
        product.setName(command.name().trim());
        product.setCategoryId(command.categoryId());
        product.setSubcategoryId(command.subcategoryId());
        product.setPurchasePrice(command.purchasePrice());
        product.setSalePrice(command.salePrice());
        product.setMinStock(command.minStock());
        product.setBarcode(normalizeBarcode(command.barcode()));
        product.setDescription(command.description() != null ? command.description().trim() : null);
        product.setImageUrl(command.imageUrl() != null ? command.imageUrl().trim() : null);
        if (command.taxRate() != null) {
            product.setTaxRate(command.taxRate());
        }
        if (command.unspscCode() != null) {
            product.setUnspscCode(command.unspscCode().trim());
        }

        Product saved = productRepository.save(product);
        releaseReplacedImage(saved, previousImageUrl);
        return toDto(saved);
    }

    /**
     * Si la foto cambió o se quitó, borra la anterior de R2 para no acumular archivos huérfanos.
     * Se omite si otro producto de la tienda sigue usando esa misma URL. El borrado ocurre tras el
     * commit y un fallo de R2 solo se registra: nunca debe revertir ni romper el guardado del producto.
     * (No se borra al desactivar un producto: es reversible y debe conservar su foto.)
     */
    private void releaseReplacedImage(Product product, String previousUrl) {
        if (previousUrl == null || previousUrl.isBlank() || Objects.equals(previousUrl, product.getImageUrl())) {
            return;
        }
        Long storeId = product.getStoreId();
        if (productRepository.existsByStoreIdAndImageUrl(storeId, previousUrl)) {
            return;
        }
        Runnable delete = () -> {
            try {
                storageFacade.deleteFileByUrl(previousUrl, "tienda-" + storeId + "/imagenes-productos/");
            } catch (Exception e) {
                log.warn("No se pudo borrar la foto anterior del producto {} de R2: {}", product.getId(), e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
        } else {
            delete.run();
        }
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
        spec = spec.and(ProductSpecifications.hasStoreId(requireCurrentStoreId()));

        Page<Product> page = productRepository.findAll(spec, pageable);
        Map<Long, LocalDate> nextExpirations = nextExpirations(page.getContent());
        return page.map(product -> productMapper.toDto(product, nextExpirations.get(product.getId())));
    }

    @Override
    public List<ProductDto> findByInternalCodes(Collection<String> internalCodes) {
        return findInBatches(internalCodes, productRepository::findByStoreIdAndInternalCodeIn);
    }

    @Override
    public List<ProductDto> findByBarcodes(Collection<String> barcodes) {
        return findInBatches(barcodes, productRepository::findByStoreIdAndBarcodeIn);
    }

    /** Parte la lista en lotes para no armar un IN gigante (la importación masiva puede traer miles de códigos). */
    private List<ProductDto> findInBatches(Collection<String> values,
                                           BiFunction<Long, Collection<String>, List<Product>> query) {
        Long storeId = requireCurrentStoreId();
        List<String> distinct = values.stream().filter(v -> v != null && !v.isBlank()).distinct().toList();
        List<ProductDto> result = new ArrayList<>();
        for (int from = 0; from < distinct.size(); from += LOOKUP_BATCH_SIZE) {
            List<String> batch = distinct.subList(from, Math.min(from + LOOKUP_BATCH_SIZE, distinct.size()));
            List<Product> products = query.apply(storeId, batch);
            Map<Long, LocalDate> nextExpirations = nextExpirations(products);
            products.forEach(product -> result.add(productMapper.toDto(product, nextExpirations.get(product.getId()))));
        }
        return result;
    }

    private ProductDto toDto(Product product) {
        return productMapper.toDto(product, nextExpiration(product));
    }

    /** Vencimiento del lote más próximo que aún tiene unidades (una consulta de lotes), o null si el producto no vence. */
    private LocalDate nextExpiration(Product product) {
        List<ProductLot> lots = productLotRepository
                .findByStoreIdAndProductIdOrderByExpirationDateAscIdAsc(product.getStoreId(), product.getId());
        return lots.isEmpty() ? null : ProductLotAllocator.allocate(lots, product.getQuantityAvailable()).nextExpiration(lots);
    }

    /** Lo mismo para varios productos con una sola consulta de lotes (listados). Los que no vencen no aparecen en el mapa. */
    private Map<Long, LocalDate> nextExpirations(Collection<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ProductLot>> lotsByProduct = productLotRepository
                .findByStoreIdAndProductIdInOrderByExpirationDateAscIdAsc(requireCurrentStoreId(),
                        products.stream().map(Product::getId).toList()).stream()
                .collect(Collectors.groupingBy(ProductLot::getProductId));
        Map<Long, LocalDate> result = new HashMap<>();
        for (Product product : products) {
            List<ProductLot> lots = lotsByProduct.get(product.getId());
            if (lots != null) {
                LocalDate next = ProductLotAllocator.allocate(lots, product.getQuantityAvailable()).nextExpiration(lots);
                if (next != null) {
                    result.put(product.getId(), next);
                }
            }
        }
        return result;
    }

    // ---- Lotes de vencimiento ----

    @Override
    @Transactional
    public void addLot(Long productId, int quantity, LocalDate expirationDate, String lotCode) {
        saveLot(requireOwnProduct(productId), quantity, expirationDate, lotCode);
    }

    @Override
    public ProductLotsDto listLots(Long productId) {
        Product product = requireOwnProduct(productId);
        List<ProductLot> lots = productLotRepository
                .findByStoreIdAndProductIdOrderByExpirationDateAscIdAsc(product.getStoreId(), productId);
        ProductLotAllocator.Result allocation = ProductLotAllocator.allocate(lots, currentStock(productId));
        List<ProductLotDto> withUnits = lots.stream()
                .filter(lot -> allocation.remainingByLotId().get(lot.getId()) > 0)
                .map(lot -> new ProductLotDto(lot.getId(), lot.getLotCode(), lot.getQuantity(),
                        allocation.remainingByLotId().get(lot.getId()), lot.getExpirationDate(), lot.getReceivedAt()))
                .toList();
        return new ProductLotsDto(withUnits, allocation.undated());
    }

    @Override
    @Transactional
    public void assignLot(Long productId, int quantity, LocalDate expirationDate, String lotCode) {
        Product product = requireOwnProduct(productId);
        int undated = ProductLotAllocator.allocate(consolidate(product), currentStock(productId)).undated();
        if (quantity > undated) {
            throw new IllegalArgumentException(undated == 0
                    ? "Este producto no tiene unidades sin fecha de vencimiento"
                    : "Solo hay " + undated + " unidades sin fecha de vencimiento en este producto");
        }
        saveLot(product, quantity, expirationDate, lotCode);
    }

    @Override
    @Transactional
    public void updateLot(Long productId, Long lotId, LocalDate expirationDate, String lotCode, Integer quantity) {
        Product product = requireOwnProduct(productId);
        if (expirationDate == null) {
            throw new IllegalArgumentException("La fecha de vencimiento del lote es obligatoria");
        }
        validateExpirationDate(expirationDate);
        String normalizedCode = normalizeLotCode(lotCode);
        if (quantity != null && quantity <= 0) {
            throw new IllegalArgumentException("Las unidades del lote deben ser mayores que cero (para quitar el lote, elimínalo)");
        }

        // Primero se fija lo ya vendido: las unidades del lote pasan a ser las que realmente le quedan.
        List<ProductLot> lots = consolidate(product);
        ProductLot lot = lots.stream().filter(l -> l.getId().equals(lotId)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Lote no encontrado con ID: " + lotId));

        if (quantity != null && quantity != lot.getQuantity()) {
            int increase = quantity - lot.getQuantity();
            if (increase > 0) {
                // Subir las unidades de un lote las toma del stock que no tiene fecha: el stock no cambia.
                int undated = ProductLotAllocator.allocate(lots, currentStock(productId)).undated();
                if (increase > undated) {
                    throw new IllegalArgumentException(undated == 0
                            ? "No hay unidades sin fecha para ampliar este lote. Sube primero el stock con una entrada o un conteo."
                            : "Solo hay " + undated + " unidades sin fecha para ampliar este lote. Sube primero el stock con una entrada o un conteo.");
                }
            }
            lot.setQuantity(quantity);
        }
        lot.setExpirationDate(expirationDate);
        lot.setLotCode(normalizedCode);
        productLotRepository.save(lot);
    }

    @Override
    @Transactional
    public void deleteLot(Long productId, Long lotId) {
        Product product = requireOwnProduct(productId);
        // Se consolida antes: así quitar este lote no revive unidades de otros lotes que ya se vendieron.
        ProductLot lot = consolidate(product).stream().filter(l -> l.getId().equals(lotId)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Lote no encontrado con ID: " + lotId));
        productLotRepository.delete(lot);
    }

    @Override
    @Transactional
    public void consolidateLots(Long productId) {
        consolidate(requireOwnProduct(productId));
    }

    /**
     * Fija en los lotes lo ya vendido: borra los lotes agotados y deja en los parciales solo lo que les queda. El reparto
     * FEFO se deriva de "total de lotes − stock"; sin esto, un lote vendido hace tiempo seguiría sumando al total y
     * reaparecería en cuanto el stock subiera sin lote o se quitara otro lote. Devuelve los lotes que quedan.
     */
    private List<ProductLot> consolidate(Product product) {
        List<ProductLot> lots = productLotRepository
                .findByStoreIdAndProductIdOrderByExpirationDateAscIdAsc(product.getStoreId(), product.getId());
        if (lots.isEmpty()) {
            return lots;
        }
        ProductLotAllocator.Result allocation = ProductLotAllocator.allocate(lots, currentStock(product.getId()));
        List<ProductLot> kept = new ArrayList<>();
        for (ProductLot lot : lots) {
            int remaining = allocation.remainingByLotId().get(lot.getId());
            if (remaining == 0) {
                productLotRepository.delete(lot);
            } else {
                if (remaining != lot.getQuantity()) {
                    lot.setQuantity(remaining);
                    productLotRepository.save(lot);
                }
                kept.add(lot);
            }
        }
        return kept;
    }

    private ProductLot saveLot(Product product, int quantity, LocalDate expirationDate, String lotCode) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad del lote debe ser mayor que cero");
        }
        if (expirationDate == null) {
            throw new IllegalArgumentException("La fecha de vencimiento del lote es obligatoria");
        }
        validateExpirationDate(expirationDate);

        ProductLot lot = new ProductLot();
        lot.setStoreId(product.getStoreId());
        lot.setProductId(product.getId());
        lot.setQuantity(quantity);
        lot.setExpirationDate(expirationDate);
        lot.setLotCode(normalizeLotCode(lotCode));
        return productLotRepository.save(lot);
    }

    /** Stock leído de la BD (no de la entidad, que dentro de una transacción con ventas puede estar desfasada). */
    private int currentStock(Long productId) {
        Integer stock = productRepository.findStockById(productId);
        return stock == null ? 0 : stock;
    }

    private static String normalizeLotCode(String lotCode) {
        if (lotCode == null || lotCode.isBlank()) {
            return null;
        }
        String trimmed = lotCode.trim();
        if (trimmed.length() > MAX_LOT_CODE_LENGTH) {
            throw new IllegalArgumentException("El código de lote no puede superar los " + MAX_LOT_CODE_LENGTH + " caracteres");
        }
        return trimmed;
    }

    private Product requireOwnProduct(Long id) {
        return productRepository.findById(id)
                .filter(this::belongsToCurrentStore)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado con ID: " + id));
    }

    /** Un código de barras en blanco se guarda como NULL: "" chocaría con UK_products_store_barcode. */
    private static String normalizeBarcode(String barcode) {
        return barcode == null || barcode.isBlank() ? null : barcode.trim();
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


    /** La fecha es opcional y una ya pasada se acepta (mercancía vencida que hay que poder registrar). */
    private static void validateExpirationDate(LocalDate expirationDate) {
        if (expirationDate != null && expirationDate.isAfter(LocalDate.now().plusYears(MAX_EXPIRATION_YEARS_AHEAD))) {
            throw new IllegalArgumentException("La fecha de vencimiento no puede superar los "
                    + MAX_EXPIRATION_YEARS_AHEAD + " años a partir de hoy");
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
        adjustStock(id, quantityDelta);
    }

    @Override
    @Transactional
    public int adjustStock(Long id, int quantityDelta) {
        Long storeId = TenantContext.getStoreId();
        int updated = storeId != null
                ? productRepository.adjustStockInStore(id, storeId, quantityDelta)
                : productRepository.adjustStock(id, quantityDelta);
        if (updated == 0) {
            // O no existe en el local, o el UPDATE condicionado rechazo dejar el stock negativo.
            Product product = productRepository.findById(id)
                    .filter(p -> storeId == null || p.getStoreId().equals(storeId))
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Producto no encontrado con ID: " + id));
            throw new IllegalArgumentException("No hay suficiente stock para el producto: " + product.getName());
        }
        return productRepository.findStockById(id);
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

