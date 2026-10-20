package com.sciencebot.pos.billing.internal.repositories;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ElectronicInvoiceRepository extends JpaRepository<ElectronicInvoice, Long> {

    Optional<ElectronicInvoice> findBySaleId(Long saleId);

    /**
     * Bloqueo pesimista de la factura de una venta para serializar emisiones concurrentes
     * (venta con emisión automática + reintento manual simultáneos) y evitar duplicar la
     * factura legal ante la DIAN.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ElectronicInvoice e WHERE e.saleId = :saleId")
    Optional<ElectronicInvoice> findBySaleIdForUpdate(@Param("saleId") Long saleId);

    List<ElectronicInvoice> findByStatus(ElectronicInvoiceStatus status);

    Page<ElectronicInvoice> findByStoreId(Long storeId, Pageable pageable);

    Page<ElectronicInvoice> findByStoreIdAndStatus(Long storeId, ElectronicInvoiceStatus status, Pageable pageable);

    Optional<ElectronicInvoice> findBySaleIdAndStoreId(Long saleId, Long storeId);
}
