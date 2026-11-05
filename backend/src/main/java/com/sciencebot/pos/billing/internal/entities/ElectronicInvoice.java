package com.sciencebot.pos.billing.internal.entities;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import jakarta.persistence.*;
import java.time.Duration;
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

    @Column(name = "legal_number", length = 50)
    private String legalNumber;

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

    /** Identificador del documento en el proveedor de facturación; se asigna una sola vez. */
    @Column(name = "provider_document_id", length = 64)
    private String providerDocumentId;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    /** Intentos de emisión desde el último reinicio manual. */
    @Column(name = "attempts", nullable = false)
    private int attempts;

    /** Reserva (lease) de la emisión en curso: mientras esté vigente ningún otro hilo emite esta factura. */
    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    /** Cuándo el reintento automático puede volver a intentarla; null = no se reintenta sola. */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

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
        invoice.storeId = Objects.requireNonNull(storeId, "El storeId es obligatorio para inicializar la factura");
        invoice.status = ElectronicInvoiceStatus.PENDING;
        return invoice;
    }

    /** Resultado de pedir la reserva de la emisión. */
    public enum ReserveOutcome { RESERVED, ALREADY_VALIDATED, IN_PROGRESS }

    /**
     * Reserva la emisión: una factura validada no se vuelve a emitir y, con una reserva vigente de otro hilo,
     * tampoco. Si no, queda PENDING, cuenta el intento y toma la reserva (que caduca pasado {@code lease}).
     */
    public ReserveOutcome reserveForEmission(LocalDateTime now, Duration lease) {
        if (this.status == ElectronicInvoiceStatus.VALIDATED) {
            return ReserveOutcome.ALREADY_VALIDATED;
        }
        if (this.processingStartedAt != null && this.processingStartedAt.plus(lease).isAfter(now)) {
            return ReserveOutcome.IN_PROGRESS;
        }
        this.status = ElectronicInvoiceStatus.PENDING;
        this.errorMessage = null;
        this.attempts++;
        this.processingStartedAt = now;
        this.nextRetryAt = null;
        return ReserveOutcome.RESERVED;
    }

    /** Cierra el intento: libera la reserva y deja programado el siguiente reintento (null = ninguno). */
    public void completeAttempt(LocalDateTime nextRetryAt) {
        this.processingStartedAt = null;
        this.nextRetryAt = nextRetryAt;
    }

    /** El proveedor todavía no resolvió el documento: sigue PENDING (no es un error) con su mensaje. */
    public void markAsPending(String message) {
        assertNotTerminal();
        this.status = ElectronicInvoiceStatus.PENDING;
        this.errorMessage = (message != null && !message.isBlank()) ? message.trim() : "Pendiente de respuesta del proveedor";
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

        this.legalNumber = legalNumber.trim();
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
        if (this.status != ElectronicInvoiceStatus.PENDING) {
            // Tras un rechazo o un fallo la nueva emisión crea otro documento en el proveedor; uno en espera se concilia.
            this.providerDocumentId = null;
        }
        this.status = ElectronicInvoiceStatus.PENDING;
        this.errorMessage = null;
        this.attempts = 0;
        this.nextRetryAt = null;
    }

    /**
     * Invariante de Negocio: Valida si la factura está en condiciones fiscales de ser enviada por correo.
     */
    public void assertCanSendEmail() {
        if (this.status != ElectronicInvoiceStatus.VALIDATED || this.legalNumber == null || this.legalNumber.isBlank()) {
            throw new IllegalStateException("La factura no ha sido validada por la DIAN (estado actual: " + getStatus() + ")");
        }
    }

    /**
     * Invariante de Negocio: Valida si la factura tiene asignado número legal para descargar PDF o XML.
     */
    public void assertCanDownloadLegalDocument() {
        if (this.legalNumber == null || this.legalNumber.isBlank()) {
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

    public String getProviderDocumentId() { return providerDocumentId; }

    /** Asigna el id del documento en el proveedor: una sola vez; repetir el mismo valor no hace nada. */
    public void assignProviderDocumentId(String providerDocumentId) {
        if (providerDocumentId == null || providerDocumentId.isBlank()) {
            throw new IllegalArgumentException("El id del documento en el proveedor es obligatorio");
        }
        String value = providerDocumentId.trim();
        if (this.providerDocumentId != null && !this.providerDocumentId.equals(value)) {
            throw new IllegalStateException("El documento ya tiene un id de proveedor y no puede cambiarse");
        }
        this.providerDocumentId = value;
    }

    /**
     * Anota el id que el proveedor le dio al documento de este intento. Mientras la factura no esté validada puede
     * cambiar (un documento rechazado se reemplaza por el de la nueva emisión); una validada nunca cambia de id.
     */
    public void trackProviderDocument(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String value = id.trim();
        if (this.status == ElectronicInvoiceStatus.VALIDATED && this.providerDocumentId != null
                && !this.providerDocumentId.equals(value)) {
            throw new IllegalStateException("Una factura VALIDATED no puede cambiar de documento en el proveedor");
        }
        this.providerDocumentId = value;
    }

    /** El proveedor ya recibió el documento y aún no hay respuesta final: se concilia en lugar de emitir de nuevo. */
    public boolean awaitsProviderResponse() {
        return this.status == ElectronicInvoiceStatus.PENDING && this.providerDocumentId != null;
    }

    public int getAttempts() { return attempts; }
    public LocalDateTime getProcessingStartedAt() { return processingStartedAt; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Long getSaleId() { return saleId; }
    public String getLegalNumber() { return legalNumber; }
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
