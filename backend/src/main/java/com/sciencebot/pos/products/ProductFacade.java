package com.sciencebot.pos.products;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductFacade {
    Optional<ProductDto> getById(Long id);
    ProductDto createProduct(CreateProductCommand command);
    ProductDto updateProduct(Long id, UpdateProductCommand command);
    void changeStatus(Long id, boolean active);
    void deleteProduct(Long id);
    Page<ProductDto> searchProducts(String search, Long categoryId, Boolean active, Pageable pageable);

    /** Productos del local actual (activos o no) cuyo código interno está en la lista. Coincidencia exacta. */
    List<ProductDto> findByInternalCodes(Collection<String> internalCodes);

    /** Productos del local actual (activos o no) cuyo código de barras está en la lista. Coincidencia exacta. */
    List<ProductDto> findByBarcodes(Collection<String> barcodes);
    void updateStock(Long id, int quantityDelta);

    /**
     * Suma {@code quantityDelta} al stock de forma atomica (sin lost-update) y devuelve el stock resultante.
     * Falla con {@link IllegalArgumentException} si quedaria negativo y con
     * {@link jakarta.persistence.EntityNotFoundException} si el producto no existe en el local.
     */
    int adjustStock(Long id, int quantityDelta);
    void updatePurchasePrice(Long id, java.math.BigDecimal newPrice);

    /**
     * Registra un lote de {@code quantity} unidades con su vencimiento para un producto del local actual. Lo usan
     * {@code inventory} y {@code purchases} al registrar una entrada: el stock ya subió, así que no se valida contra
     * las unidades sin fecha. {@code lotCode} es opcional (máx. 50). La fecha pasada se acepta; más de 20 años, no.
     */
    void addLot(Long productId, int quantity, LocalDate expirationDate, String lotCode);

    /** Lotes que todavía tienen unidades (del que vence primero al último) y cuántas unidades del stock no tienen fecha. */
    ProductLotsDto listLots(Long productId);

    /**
     * Le pone fecha a stock que ya estaba sin ella: crea un lote sin mover stock. Falla con
     * {@link IllegalArgumentException} si {@code quantity} supera las unidades sin fecha.
     */
    void assignLot(Long productId, int quantity, LocalDate expirationDate, String lotCode);

    /**
     * Corrige la fecha, el código y, si {@code quantity} no es null, las unidades de un lote (las unidades que quedan
     * en él). El stock del producto no cambia: subir las unidades las toma de las unidades sin fecha (falla con
     * {@link IllegalArgumentException} si no alcanzan) y bajarlas las devuelve a ese grupo.
     */
    void updateLot(Long productId, Long lotId, LocalDate expirationDate, String lotCode, Integer quantity);

    /** Quita un lote; el stock no cambia y las unidades que le quedaban pasan a "sin fecha". */
    void deleteLot(Long productId, Long lotId);

    /**
     * Fija en los lotes lo que ya se vendió: borra los lotes agotados y deja en cada lote parcial solo las unidades
     * que le quedan. {@code inventory} lo llama antes de cualquier movimiento que suba el stock, para que una subida
     * posterior no "reviva" unidades de un lote que ya se había vendido. Es idempotente.
     */
    void consolidateLots(Long productId);
}
