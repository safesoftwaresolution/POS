package com.sciencebot.pos.stores.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "stores")
@Getter @Setter
public class StoreEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "store_category_id")
    private Long storeCategoryId;
    @Column(length = 20)
    private String phone;
    @Column(nullable = false, length = 100, unique = true)
    private String email;
    @Column(length = 255)
    private String website;
    @Column(length = 255)
    private String address;
    @Column(name = "tax_id", length = 50)
    private String taxId;
    @Column(nullable = false, length = 30)
    private String status = "PENDING_VERIFICATION";
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;
    @Column(name = "rejection_reason")
    private String rejectionReason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}