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
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private com.sciencebot.pos.stores.StoreTemplate template = com.sciencebot.pos.stores.StoreTemplate.RETAIL;
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