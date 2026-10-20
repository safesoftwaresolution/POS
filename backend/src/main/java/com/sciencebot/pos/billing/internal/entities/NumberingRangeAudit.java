package com.sciencebot.pos.billing.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro de un cambio sobre un rango de numeración (creación, eliminación, activación,
 * desactivación, cambio de consecutivo o elección del rango activo del local). Estos cambios
 * afectan la numeración legal de facturas ante la DIAN, así que quedan registrados con quién y
 * cuándo los hizo.
 */
@Entity
@Table(name = "numbering_range_audit")
@Getter
@Setter
public class NumberingRangeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "range_id", nullable = false)
    private Long rangeId;

    @Column(name = "action", nullable = false, length = 20)
    private String action; // CREATE, DELETE, ACTIVATE, DEACTIVATE, UPDATE_CURRENT, SET_DEFAULT

    @Column(name = "previous_value", length = 50)
    private String previousValue;

    @Column(name = "new_value", length = 50)
    private String newValue;

    @Column(name = "performed_by", length = 100)
    private String performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
