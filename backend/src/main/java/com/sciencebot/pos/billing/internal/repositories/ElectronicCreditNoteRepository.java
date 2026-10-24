package com.sciencebot.pos.billing.internal.repositories;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import com.sciencebot.pos.billing.internal.entities.ElectronicCreditNote;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ElectronicCreditNoteRepository extends JpaRepository<ElectronicCreditNote, Long> {

    Optional<ElectronicCreditNote> findByReferenceCode(String referenceCode);

    Optional<ElectronicCreditNote> findByReferenceCodeAndStoreId(String referenceCode, Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ElectronicCreditNote c WHERE c.referenceCode = :referenceCode")
    Optional<ElectronicCreditNote> findByReferenceCodeForUpdate(@Param("referenceCode") String referenceCode);

    List<ElectronicCreditNote> findByBillNumberAndStoreId(String billNumber, Long storeId);

    Optional<ElectronicCreditNote> findByCreditNoteNumber(String creditNoteNumber);

    Page<ElectronicCreditNote> findByStoreId(Long storeId, Pageable pageable);

    Page<ElectronicCreditNote> findByStoreIdAndStatus(Long storeId, ElectronicInvoiceStatus status, Pageable pageable);

    List<ElectronicCreditNote> findBySaleId(Long saleId);
}
