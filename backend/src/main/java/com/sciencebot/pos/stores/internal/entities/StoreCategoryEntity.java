package com.sciencebot.pos.stores.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_categories")
@Getter @Setter
public class StoreCategoryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    @Column(length = 255)
    private String description;
    @Column(nullable = false)
    private boolean active = true;
    /** Templates every store of this category runs with (V36). The V33 column `template` is no longer mapped. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "store_category_templates", joinColumns = @JoinColumn(name = "category_id"))
    @Column(name = "template", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private java.util.Set<com.sciencebot.pos.stores.StoreTemplate> templates = new java.util.LinkedHashSet<>();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private com.sciencebot.pos.stores.StoreTheme theme = com.sciencebot.pos.stores.StoreTheme.DEFAULT;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}