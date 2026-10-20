package com.sciencebot.pos.settings.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Encabezado de un tema de parámetros / catálogo configurable
 * (p. ej. PAYMENT_METHODS, RETURN_REASONS, DELIVERY_ZONES).
 */
@Entity
@Table(name = "parameter_topics", uniqueConstraints = {
        @UniqueConstraint(name = "UK_parameter_topics_code", columnNames = "code")
})
@Getter
@Setter
public class ParameterTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    /** Marca los temas base del sistema para protegerlos de borrado/renombrado. */
    @Column(name = "is_system", nullable = false)
    private boolean system = false;

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
