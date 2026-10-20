package com.sciencebot.pos.categories.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_subcategories", uniqueConstraints = {
        @UniqueConstraint(name = "UK_subcategories_store_category_name", columnNames = {"store_id", "category_id", "name"})
})
@Getter
@Setter
public class Subcategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tienda dueña de esta subcategoría; ninguna otra tienda la ve ni la modifica. */
    @Column(name = "store_id", nullable = false)
    private Long storeId;

    /** Categoría global (SUPER_ADMIN) bajo la que se agrupa esta subcategoría. */
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

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
