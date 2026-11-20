package com.sciencebot.pos.businesses.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Immutable record of a back-office change to a business. It keeps a copy of the data (no foreign keys),
 * so it stays readable even if the business or the store is deleted later.
 */
@Entity
@Table(name = "business_audit")
@Getter
@Setter
public class BusinessAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "business_name", length = 100, updatable = false)
    private String businessName;

    /** CREATED, UPDATED, BRANCH_ADDED or BRANCH_REMOVED. */
    @Column(nullable = false, length = 20, updatable = false)
    private String action;

    @Column(name = "affected_store_id", updatable = false)
    private Long affectedStoreId;

    @Column(length = 255, updatable = false)
    private String detail;

    @Column(name = "performed_by", length = 100, updatable = false)
    private String performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
