package com.sciencebot.pos.billing.internal.entities;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Rango de numeración local de un local, entorno y tipo de documento (01 factura, 91 nota crédito,
 * 92 nota débito). Con Alegra el sistema envía el consecutivo y la resolución en cada documento;
 * la asignación del número vive en FEAT-023.
 */
@Entity
@Table(name = "billing_numbering_range")
@Getter
public class NumberingRange {

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

    @Column(name = "prefix", nullable = false, length = 4)
    private String prefix;

    @Column(name = "resolution_number", length = 14)
    private String resolutionNumber;

    @Column(name = "min_number", nullable = false)
    private long minNumber;

    @Column(name = "max_number", nullable = false)
    private long maxNumber;

    @Column(name = "next_number", nullable = false)
    private long nextNumber;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "technical_key", length = 255)
    private String technicalKey;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected NumberingRange() {}

    public NumberingRange(Long storeId, BillingEnvironment environment, String document, String prefix,
                          String resolutionNumber, long minNumber, long maxNumber,
                          LocalDate startDate, LocalDate endDate, String technicalKey) {
        if (minNumber > maxNumber) {
            throw new IllegalArgumentException("El número inicial no puede ser mayor que el final");
        }
        this.storeId = storeId;
        this.environment = environment;
        this.document = document;
        this.prefix = prefix;
        this.resolutionNumber = resolutionNumber;
        this.minNumber = minNumber;
        this.maxNumber = maxNumber;
        this.nextNumber = minNumber;
        this.startDate = startDate;
        this.endDate = endDate;
        this.technicalKey = technicalKey;
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
