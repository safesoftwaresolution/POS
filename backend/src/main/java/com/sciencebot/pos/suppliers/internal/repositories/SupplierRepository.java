package com.sciencebot.pos.suppliers.internal.repositories;

import com.sciencebot.pos.suppliers.internal.entities.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsByStoreIdAndTaxId(Long storeId, String taxId);

    Page<Supplier> findAllByStoreId(Long storeId, Pageable pageable);

    @Query("SELECT s FROM Supplier s WHERE s.storeId = :storeId AND (" +
           "LOWER(s.companyName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.taxId) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Supplier> searchSuppliers(@Param("storeId") Long storeId, @Param("search") String search, Pageable pageable);
}
