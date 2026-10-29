package com.sciencebot.pos.services.internal.repositories;

import com.sciencebot.pos.services.internal.entities.ServiceItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {

    Optional<ServiceItem> findByIdAndStoreId(Long id, Long storeId);

    boolean existsByStoreIdAndCode(Long storeId, String code);

    /**
     * Búsqueda parcial por nombre o código dentro del local, con filtros opcionales. {@code search} llega en
     * minúsculas (o null) desde el servicio.
     */
    @Query("select s from ServiceItem s where s.storeId = :storeId "
            + "and (:search is null or lower(s.name) like concat('%', :search, '%') "
            + "or lower(s.code) like concat('%', :search, '%')) "
            + "and (:categoryId is null or s.categoryId = :categoryId) "
            + "and (:active is null or s.active = :active)")
    Page<ServiceItem> searchInStore(@Param("storeId") Long storeId, @Param("search") String search,
                                    @Param("categoryId") Long categoryId, @Param("active") Boolean active,
                                    Pageable pageable);

    // Nota: intencionalmente NO filtra por local, igual que ProductRepository.existsByCategoryIdAndActiveTrue:
    // las categorías son globales y el chequeo se dispara al borrar una categoría de todos los locales.
    boolean existsByCategoryIdAndActiveTrue(Long categoryId);
}
