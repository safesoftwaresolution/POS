package com.sciencebot.pos.billing.internal.entities;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entidad de Dominio Rico que representa una Factura Electrónica colombiana ante la DIAN.
 * Encapsula la máquina de estados, garantiza la inmutabilidad tras la validación
 * y protege las invariantes fiscales.
 */
@Entity
@Table(name = "electronic_invoices")
public class ElectronicInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "sale_id", nullable = false, unique = true, updatable = false)
    private Long saleId;

    @Column(name = "factus_number", length = 50)
    private String factusNumber;

    @Column(name = "cufe", length = 255)
    private String cufe;

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

    /**
     * Constructor protegido para uso exclusivo del proveedor JPA.
     */
    protected ElectronicInvoice() {}

    /**
     * Factory method: Inicializa un registro de factura electrónica para una venta.
     * Toda factura nace en estado PENDING y asociada unívocamente a una venta y tienda.
     */
    public static ElectronicInvoice initialize(Long saleId, Long storeId) {
        Objects.requireNonNull(saleId, "El saleId es obligatorio para inicializar la factura");
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.saleId = saleId;
        invoice.storeId = storeId != null ? storeId : 1L;
        invoice.status = ElectronicInvoiceStatus.PENDING;
        return invoice;
    }

    /**
     * Invariante de Negocio: Marca la factura como VALIDADA formalmente por la DIAN.
     * Asigna CUFE, número oficial y marca de tiempo legal.
     * Una factura validada se convierte en fiscalmente inmutable.
     */
    public void markAsValidated(String legalNumber, String cufe, String qrCode, String pdfUrl, LocalDateTime validatedAt) {
        if (this.status == ElectronicInvoiceStatus.VALIDATED) {
            return; // Idempotencia ante reenvíos
        }
        if (legalNumber == null || legalNumber.isBlank()) {
            throw new IllegalStateException("No se puede certificar una factura sin número oficial asignado por la DIAN");
        }
        if (cufe == null || cufe.isBlank()) {
            throw new IllegalStateException("El CUFE es obligatorio para certificar una factura electrónica ante la DIAN");
        }

        this.factusNumber = legalNumber.trim();
        this.cufe = cufe.trim();
        this.qrCode = qrCode;
        this.pdfUrl = pdfUrl;
        this.status = ElectronicInvoiceStatus.VALIDATED;
        this.errorMessage = null;
        this.validatedAt = (validatedAt != null) ? validatedAt : LocalDateTime.now();
    }

    /**
     * Invariante de Negocio: Marca la factura como RECHAZADA por la DIAN.
     */
    public void markAsRejected(String reason) {
        assertNotTerminal();
        this.status = ElectronicInvoiceStatus.REJECTED;
        this.errorMessage = (reason != null && !reason.isBlank()) ? reason.trim() : "Factura rechazada por la DIAN";
    }

    /**
     * Invariante de Negocio: Registra un fallo técnico o error de proveedor.
     */
    public void markAsError(String technicalError) {
        assertNotTerminal();
        this.status = ElectronicInvoiceStatus.ERROR;
        this.errorMessage = (technicalError != null && !technicalError.isBlank()) ? technicalError.trim() : "Error en proceso de facturación";
    }

    /**
     * Invariante de Negocio: Prepara la factura para un reintento de emisión.
     * Solo estados transitorios o fallidos (ERROR o REJECTED) pueden reintentarse.
     */
    public void prepareForRetry() {
        if (this.status == ElectronicInvoiceStatus.VALIDATED) {
            throw new IllegalArgumentException("La factura ya está validada y no puede reintentarse");
        }
        this.status = ElectronicInvoiceStatus.PENDING;
        this.errorMessage = null;
    }

    /**
     * Invariante de Negocio: Valida si la factura está en condiciones fiscales de ser enviada por correo.
     */
    public void assertCanSendEmail() {
        if (this.status != ElectronicInvoiceStatus.VALIDATED || this.factusNumber == null || this.factusNumber.isBlank()) {
            throw new IllegalStateException("La factura no ha sido validada por la DIAN (estado actual: " + getStatus() + ")");
        }
    }

    /**
     * Invariante de Negocio: Valida si la factura tiene asignado número legal para descargar PDF o XML.
     */
    public void assertCanDownloadLegalDocument() {
        if (this.factusNumber == null || this.factusNumber.isBlank()) {
            throw new IllegalStateException("La factura no tiene número legal asignado");
        }
    }

    public boolean isValidated() {
        return this.status == ElectronicInvoiceStatus.VALIDATED;
    }

    public boolean canBeRetried() {
        return this.status == ElectronicInvoiceStatus.ERROR || this.status == ElectronicInvoiceStatus.REJECTED;
    }

    /**
     * Garantiza retrocompatibilidad en caso de registros heredados sin storeId persistido.
     */
    public void assignStoreIdIfMissing(Long storeId) {
        if (this.storeId == null && storeId != null) {
            this.storeId = storeId;
        }
    }

    private void assertNotTerminal() {
        if (this.status == ElectronicInvoiceStatus.VALIDATED) {
            throw new IllegalStateException("Una factura en estado VALIDATED es fiscalmente inmutable y no puede modificarse");
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

    // --- Getters de solo lectura ---

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Long getSaleId() { return saleId; }
    public String getFactusNumber() { return factusNumber; }
    public String getCufe() { return cufe; }
    public String getQrCode() { return qrCode; }
    public ElectronicInvoiceStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public String getPdfUrl() { return pdfUrl; }
    public LocalDateTime getValidatedAt() { return validatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /** Setter exclusivo para soporte de identificadores en pruebas o infraestructura. */
    public void setId(Long id) { this.id = id; }
}
