package com.sciencebot.pos.customers.internal.repositories;

import com.sciencebot.pos.customers.internal.entities.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByStoreIdAndIdentification(Long storeId, String identification);

    boolean existsByStoreIdAndIdentification(Long storeId, String identification);

    Page<Customer> findAllByStoreId(Long storeId, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.storeId = :storeId AND (" +
           "LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.identification) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Customer> searchCustomers(@Param("storeId") Long storeId, @Param("search") String search, Pageable pageable);
}
