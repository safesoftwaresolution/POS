package com.sciencebot.pos.billing.internal.repositories;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import com.sciencebot.pos.billing.internal.entities.ElectronicDebitNote;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ElectronicDebitNoteRepository extends JpaRepository<ElectronicDebitNote, Long> {

    Optional<ElectronicDebitNote> findByReferenceCode(String referenceCode);

    Optional<ElectronicDebitNote> findByReferenceCodeAndStoreId(String referenceCode, Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM ElectronicDebitNote d WHERE d.referenceCode = :referenceCode")
    Optional<ElectronicDebitNote> findByReferenceCodeForUpdate(@Param("referenceCode") String referenceCode);

    List<ElectronicDebitNote> findByBillNumberAndStoreId(String billNumber, Long storeId);

    Optional<ElectronicDebitNote> findByDebitNoteNumber(String debitNoteNumber);

    Page<ElectronicDebitNote> findByStoreId(Long storeId, Pageable pageable);

    Page<ElectronicDebitNote> findByStoreIdAndStatus(Long storeId, ElectronicInvoiceStatus status, Pageable pageable);

    List<ElectronicDebitNote> findBySaleId(Long saleId);

}
