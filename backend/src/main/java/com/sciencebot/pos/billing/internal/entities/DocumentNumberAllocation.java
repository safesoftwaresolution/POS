package com.sciencebot.pos.billing.internal.entities;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Libro de asignaciones: una referencia (venta o nota) recibe un solo número de un rango, aunque la
 * emisión se reintente. La lógica de asignación vive en FEAT-023.
 */
@Entity
@Table(name = "billing_document_number")
@Getter
public class DocumentNumberAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, updatable = false, length = 12)
    private BillingEnvironment environment;

    @Column(name = "document", nullable = false, updatable = false, length = 2)
    private String document;

    @Column(name = "reference_code", nullable = false, updatable = false, length = 100)
    private String referenceCode;

    @Column(name = "range_id", nullable = false, updatable = false)
    private Long rangeId;

    @Column(name = "prefix", nullable = false, updatable = false, length = 4)
    private String prefix;

    @Column(name = "number", nullable = false, updatable = false)
    private long number;

    @Column(name = "idempotency_key", length = 36)
    private String idempotencyKey;

    @Column(name = "completed", nullable = false)
    private boolean completed = false;

    @Column(name = "outcome", length = 24)
    private String outcome;

    @Column(name = "provider_document_id", length = 100)
    private String providerDocumentId;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected DocumentNumberAllocation() {}

    public DocumentNumberAllocation(Long storeId, BillingEnvironment environment, String document,
                                    String referenceCode, Long rangeId, String prefix, long number,
                                    String idempotencyKey) {
        this.storeId = storeId;
        this.environment = environment;
        this.document = document;
        this.referenceCode = referenceCode;
        this.rangeId = rangeId;
        this.prefix = prefix;
        this.number = number;
        this.idempotencyKey = idempotencyKey;
    }

    /** Cambia la llave de idempotencia conservando el número (tras una respuesta final no aceptada). */
    public void rotateIdempotencyKey(String newKey) {
        if (!"REJECTED".equals(outcome) && !"FAILED".equals(outcome)) {
            throw new IllegalStateException("No se puede cambiar la llave de una emisión aceptada o de resultado incierto");
        }
        this.idempotencyKey = newKey;
        this.completed = false;
        this.outcome = null;
        this.providerDocumentId = null;
        this.requestPayload = null;
        this.requestHash = null;
        this.responsePayload = null;
    }

    public void freezeRequest(String payload, String hash) {
        if (requestPayload == null) {
            requestPayload = payload;
            requestHash = hash;
        }
    }

    public void recordResponse(String outcome, String documentId, String payload) {
        if ("ACCEPTED".equals(this.outcome)) return;
        this.outcome = outcome;
        this.providerDocumentId = documentId;
        this.responsePayload = payload;
        this.completed = "ACCEPTED".equals(outcome) || "REJECTED".equals(outcome) || "FAILED".equals(outcome);
    }

    /** Marca que el proveedor ya recibió el documento con este número. */
    public void markCompleted() {
        this.completed = true;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
