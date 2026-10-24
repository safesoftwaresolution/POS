package com.sciencebot.pos.stores.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro inmutable de un cambio de estado de un local (registro, aprobación, rechazo,
 * suspensión...). Solo se inserta; nunca se actualiza ni se elimina.
 */
@Entity
@Table(name = "store_status_audit")
@Getter
@Setter
public class StoreStatusAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    /** Copia de los datos del local al momento del evento: sobrevive a la eliminación del local. */
    @Column(name = "store_name", length = 100, updatable = false)
    private String storeName;

    @Column(name = "store_email", length = 100, updatable = false)
    private String storeEmail;

    @Column(name = "store_tax_id", length = 50, updatable = false)
    private String storeTaxId;

    @Column(name = "previous_status", length = 30, updatable = false)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 30, updatable = false)
    private String newStatus;

    @Column(name = "reason", length = 255, updatable = false)
    private String reason;

    @Column(name = "performed_by", length = 100, updatable = false)
    private String performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
