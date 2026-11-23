package com.sciencebot.pos.businesses.internal.entities;

import com.sciencebot.pos.businesses.BillingMode;
import com.sciencebot.pos.businesses.CatalogMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "businesses")
@Getter
@Setter
public class BusinessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "main_store_id", nullable = false)
    private Long mainStoreId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode", nullable = false, length = 20)
    private BillingMode billingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "catalog_mode", nullable = false, length = 20)
    private CatalogMode catalogMode;

    /** Branches allowed besides the main store (the main store does not count). */
    @Column(name = "max_branches", nullable = false)
    private int maxBranches;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
