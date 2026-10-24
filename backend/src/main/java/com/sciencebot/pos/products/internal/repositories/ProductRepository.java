package com.sciencebot.pos.products.internal.repositories;

import com.sciencebot.pos.products.internal.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * Ajuste de stock ATOMICO: una sola sentencia UPDATE que suma el delta y solo se aplica si el
     * resultado no queda negativo. La fila queda bloqueada hasta el commit, asi que ventas/compras
     * concurrentes del mismo producto se serializan sin lost-update ni sobreventa.
     *
     * <p>Un {@code SELECT ... FOR UPDATE} + modificar la entidad NO sirve aqui: dentro de la misma
     * transaccion el producto ya suele estar cargado (la venta lo lee antes) y Hibernate devuelve esa
     * instancia con el valor viejo en vez de releerlo, con lo que se pisaban las actualizaciones.
     *
     * @return filas afectadas: 0 si el producto no existe en el local o el stock quedaria negativo
     */
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.quantityAvailable = p.quantityAvailable + :delta "
            + "where p.id = :id and p.storeId = :storeId and p.quantityAvailable + :delta >= 0")
    int adjustStockInStore(@Param("id") Long id, @Param("storeId") Long storeId, @Param("delta") int delta);

    /** Igual que {@link #adjustStockInStore} para llamadas sin local en contexto (seeders, tareas internas). */
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.quantityAvailable = p.quantityAvailable + :delta "
            + "where p.id = :id and p.quantityAvailable + :delta >= 0")
    int adjustStock(@Param("id") Long id, @Param("delta") int delta);

    /** Stock actual leido de la BD (proyeccion escalar: no pasa por el contexto de persistencia). */
    @Query("select p.quantityAvailable from Product p where p.id = :id")
    Integer findStockById(@Param("id") Long id);

    Optional<Product> findByStoreIdAndInternalCode(Long storeId, String internalCode);
    Optional<Product> findByStoreIdAndBarcode(Long storeId, String barcode);

    List<Product> findByStoreIdAndInternalCodeIn(Long storeId, Collection<String> internalCodes);
    List<Product> findByStoreIdAndBarcodeIn(Long storeId, Collection<String> barcodes);

    boolean existsByStoreIdAndInternalCode(Long storeId, String internalCode);
    boolean existsByStoreIdAndBarcode(Long storeId, String barcode);

    // Nota: intencionalmente NO se filtran por local. Las categorias son globales (solo
    // SUPER_ADMIN las administra) y este chequeo se dispara al borrar una categoria global,
    // por lo que debe contar productos con esa categoria en TODOS los locales.
    boolean existsByCategoryIdAndActiveTrue(Long categoryId);
    int countByCategoryIdAndActiveTrue(Long categoryId);

    // Subcategorias: a diferencia de categories (global), aqui SI corresponde acotar por
    // tienda -- pero SubcategoryDeleteValidator/-ProductCountProvider ya reciben el id de
    // una subcategoria puntual (ya es unica por tienda), asi que no hace falta filtrar por
    // store_id de nuevo aqui.
    boolean existsBySubcategoryIdAndActiveTrue(Long subcategoryId);
    int countBySubcategoryIdAndActiveTrue(Long subcategoryId);

}

