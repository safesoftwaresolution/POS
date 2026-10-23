package com.sciencebot.pos.billing.internal.entities;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entidad de Dominio Rico que representa una Nota Crédito Electrónica colombiana ante la DIAN.
 * Encapsula la máquina de estados, garantiza la inmutabilidad tras la validación
 * y protege las invariantes fiscales.
 */
@Entity
@Table(name = "electronic_credit_notes")
public class ElectronicCreditNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "sale_id", updatable = false)
    private Long saleId;

    @Column(name = "reference_code", nullable = false, unique = true, length = 100, updatable = false)
    private String referenceCode;

    @Column(name = "bill_number", nullable = false, length = 50, updatable = false)
    private String billNumber;

    @Column(name = "credit_note_number", length = 50)
    private String creditNoteNumber;

    @Column(name = "correction_concept_code", nullable = false, length = 10)
    private String correctionConceptCode;

    @Column(name = "customization_id", nullable = false, length = 10)
    private String customizationId;

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

    protected ElectronicCreditNote() {}

    /**
     * Factory method: Inicializa un registro de nota crédito electrónica en estado PENDING.
     */
    public static ElectronicCreditNote initialize(
            Long storeId,
            Long saleId,
            String referenceCode,
            String billNumber,
            String correctionConceptCode,
            String customizationId
    ) {
        Objects.requireNonNull(storeId, "El storeId es obligatorio para inicializar la nota crédito");
        Objects.requireNonNull(referenceCode, "El referenceCode es obligatorio");
        Objects.requireNonNull(billNumber, "El billNumber es obligatorio");
        Objects.requireNonNull(correctionConceptCode, "El correctionConceptCode es obligatorio");

        ElectronicCreditNote note = new ElectronicCreditNote();
        note.storeId = storeId;
        note.saleId = saleId;
        note.referenceCode = referenceCode.trim();
        note.billNumber = billNumber.trim();
        note.correctionConceptCode = correctionConceptCode.trim();
        note.customizationId = (customizationId != null && !customizationId.isBlank()) ? customizationId.trim() : "20";
        note.status = ElectronicInvoiceStatus.PENDING;
        return note;
    }

    /**
     * Invariante de Negocio: Marca la nota crédito como VALIDADA formalmente por la DIAN.
     * Asigna CUDE, número oficial de nota crédito y marca de tiempo legal.
     */
    public void markAsValidated(String creditNoteNumber, String cude, String qrCode, String pdfUrl, LocalDateTime validatedAt) {
        if (this.status == ElectronicInvoiceStatus.VALIDATED) {
            return; // Idempotencia ante reenvíos
        }
        if (creditNoteNumber == null || creditNoteNumber.isBlank()) {
            throw new IllegalStateException("No se puede certificar una nota crédito sin número oficial asignado por la DIAN/Factus");
        }

        this.creditNoteNumber = creditNoteNumber.trim();
        this.cude = (cude != null) ? cude.trim() : null;
        this.qrCode = qrCode;
        this.pdfUrl = pdfUrl;
        this.status = ElectronicInvoiceStatus.VALIDATED;
        this.errorMessage = null;
        this.validatedAt = (validatedAt != null) ? validatedAt : LocalDateTime.now();
    }

    /**
     * Invariante de Negocio: Marca la nota crédito como RECHAZADA por la DIAN.
     */
    public void markAsRejected(String reason) {
        assertNotTerminal();
        this.status = ElectronicInvoiceStatus.REJECTED;
        this.errorMessage = (reason != null && !reason.isBlank()) ? reason.trim() : "Nota crédito rechazada por la DIAN";
    }

    /**
     * Invariante de Negocio: Registra un fallo técnico o error de proveedor.
     */
    public void markAsError(String technicalError) {
        assertNotTerminal();
        this.status = ElectronicInvoiceStatus.ERROR;
        this.errorMessage = (technicalError != null && !technicalError.isBlank()) ? technicalError.trim() : "Error en proceso de nota crédito";
    }

    /**
     * Invariante de Negocio: Prepara la nota crédito para un reintento de emisión.
     */
    public void prepareForRetry() {
        if (this.status == ElectronicInvoiceStatus.VALIDATED) {
            throw new IllegalArgumentException("La nota crédito ya está validada y no puede reintentarse");
        }
        this.status = ElectronicInvoiceStatus.PENDING;
        this.errorMessage = null;
    }

    public boolean isValidated() {
        return this.status == ElectronicInvoiceStatus.VALIDATED;
    }

    public boolean canBeRetried() {
        return this.status == ElectronicInvoiceStatus.ERROR || this.status == ElectronicInvoiceStatus.REJECTED;
    }

    private void assertNotTerminal() {
        if (this.status == ElectronicInvoiceStatus.VALIDATED) {
            throw new IllegalStateException("Una nota crédito en estado VALIDATED es fiscalmente inmutable y no puede modificarse");
        }
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // --- Getters ---

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Long getSaleId() { return saleId; }
    public String getReferenceCode() { return referenceCode; }
    public String getBillNumber() { return billNumber; }
    public String getCreditNoteNumber() { return creditNoteNumber; }
    public String getCorrectionConceptCode() { return correctionConceptCode; }
    public String getCustomizationId() { return customizationId; }
    public String getCude() { return cude; }
    public String getQrCode() { return qrCode; }
    public ElectronicInvoiceStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public String getPdfUrl() { return pdfUrl; }
    public LocalDateTime getValidatedAt() { return validatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
}
