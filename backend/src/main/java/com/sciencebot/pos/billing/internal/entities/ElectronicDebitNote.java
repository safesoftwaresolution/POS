package com.sciencebot.pos.billing.internal.entities;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import com.sciencebot.pos.shared.validation.Preconditions;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "electronic_debit_notes")
public class ElectronicDebitNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "sale_id", updatable = false)
    private Long saleId;

    @Column(name = "reference_code", nullable = false, unique = true, length = 100, updatable = false)
    private String referenceCode;

    @Column(name = "correction_concept_code", nullable = false, length = 10)
    private String correctionConceptCode;

    @Column(name = "customization_id", nullable = false, length = 10)
    private String customizationId;

    @Column(name = "bill_number", nullable = false, length = 50, updatable = false)
    private String billNumber;

    @Column(name = "debit_note_number", length = 50)
    private String debitNoteNumber;

    @Column(name = "cude", length = 255)
    private String cude;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ElectronicInvoiceStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)  
    private LocalDateTime updatedAt;

    protected ElectronicDebitNote() {}

    public static ElectronicDebitNote initialize(
        Long storeId,
        Long saleId,
        String referenceCode,
        String billNumber,
        String correctionConceptCode,
        String customizationId
    ) {
        ElectronicDebitNote note = new ElectronicDebitNote();
        note.storeId = Preconditions.requireNotNull(storeId, "El storeId no puede ser nulo");
        note.saleId = saleId;
        note.referenceCode = Preconditions.requireNotBlank(referenceCode, "El referenceCode no puede ser nulo o vacío");
        note.billNumber = Preconditions.requireNotBlank(billNumber, "El billNumber no puede ser nulo o vacío");
        note.correctionConceptCode = Preconditions.requireNotBlank(correctionConceptCode, "El correctionConceptCode no puede ser nulo o vacío");
        note.customizationId = Preconditions.defaultIfBlank(customizationId, "30");
        note.status = ElectronicInvoiceStatus.PENDING;
        return note;
    }

    public void markAsValidated(String debitNoteNumber, String cude, String qrCode, 
        String pdfUrl, LocalDateTime validatedAt) {

        if (this.status == ElectronicInvoiceStatus.VALIDATED) {
            return; // Idempotencia ante reenvíos
        }
        // Se valida antes de mutar: un número faltante no debe dejar la nota a medio certificar
        this.debitNoteNumber = Preconditions.requireNotBlank(debitNoteNumber, "El debitNoteNumber no puede ser nulo o vacío");
        this.cude = Preconditions.defaultIfBlank(cude, null);
        this.qrCode = qrCode;
        this.pdfUrl = pdfUrl;
        this.status = ElectronicInvoiceStatus.VALIDATED;
        this.errorMessage = null;
        this.validatedAt = (validatedAt != null) ? validatedAt : LocalDateTime.now();
    }

    public void markAsRejected(String errorMessage) {
        if (this.status == ElectronicInvoiceStatus.VALIDATED) {
            throw new IllegalStateException("La nota débito ya ha sido validada y no puede ser marcada como rechazada");
        }
        this.status = ElectronicInvoiceStatus.REJECTED;
        this.errorMessage = Preconditions.requireNotBlank(errorMessage, "El errorMessage no puede ser nulo o vacío");
    }

    public void markAsError(String errorMessage) {
        if (this.status == ElectronicInvoiceStatus.VALIDATED) {
            throw new IllegalStateException("La nota débito ya ha sido validada y no puede ser marcada como error");
        }
        this.status = ElectronicInvoiceStatus.ERROR;
        this.errorMessage = Preconditions.requireNotBlank(errorMessage, "El errorMessage no puede ser nulo o vacío");
    }

    public void prepareForResend() {
        if (this.status == ElectronicInvoiceStatus.VALIDATED) {
            throw new IllegalStateException("La nota débito ya ha sido validada y no puede ser reenviada");
        }
        this.status = ElectronicInvoiceStatus.PENDING;
        this.errorMessage = null;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }


    public Long getId() {
        return id;
    }
    public Long getStoreId() {
        return storeId;
    }
    public Long getSaleId() {
        return saleId;
    }
    public String getReferenceCode() {
        return referenceCode;
    }
    public String getBillNumber() {
        return billNumber;
    }
    public String getDebitNoteNumber() {
        return debitNoteNumber;
    }
    public String getCude() {
        return cude;
    }
    public String getQrCode() {
        return qrCode;
    }
    public ElectronicInvoiceStatus getStatus() {
        return status;
    }
    public boolean isValidated() {
        return this.status == ElectronicInvoiceStatus.VALIDATED;
    }
    public boolean canBeRetried() {
        return this.status == ElectronicInvoiceStatus.ERROR || this.status == ElectronicInvoiceStatus.REJECTED;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    public String getPdfUrl() {
        return pdfUrl;
    }
    public java.time.LocalDateTime getValidatedAt() {
        return validatedAt;
    }
    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public java.time.LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public String getCustomizationId() {
        return customizationId;
    }
    public String getCorrectionConceptCode() {
        return correctionConceptCode;
    }
    public void setId(Long id) {
        this.id = id;
    }

    





    

    

}
