package com.sciencebot.pos.services.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/** Categoría del catálogo global de servicios; se ofrece a las categorías de local enlazadas (ninguna = a todas). */
@Entity
@Table(name = "service_categories", uniqueConstraints = {
        @UniqueConstraint(name = "UK_service_categories_name", columnNames = "name")
})
@Getter
@Setter
public class ServiceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_category_store_categories", joinColumns = @JoinColumn(name = "service_category_id"))
    @Column(name = "store_category_id", nullable = false)
    private Set<Long> storeCategoryIds = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
