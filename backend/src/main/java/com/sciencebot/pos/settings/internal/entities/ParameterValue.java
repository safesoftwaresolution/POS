package com.sciencebot.pos.settings.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Valor concreto dentro de un tema de parámetros (p. ej. 'CASH' -> 'Efectivo').
 * El borrado es lógico mediante {@code active = false}.
 */
@Entity
@Table(name = "parameter_values", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_param_value_topic_code", columnNames = {"topic_id", "code"})
})
@Getter
@Setter
public class ParameterValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private ParameterTopic topic;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    /** Dato auxiliar opcional: porcentaje, costo, código DIAN, etc. */
    @Column(name = "extra_value", length = 255)
    private String extraValue;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
