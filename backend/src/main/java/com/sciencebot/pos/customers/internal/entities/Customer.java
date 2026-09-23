package com.sciencebot.pos.customers.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers", uniqueConstraints = {
        @UniqueConstraint(name = "UK_customers_store_identification", columnNames = {"store_id", "identification"})
})
@Getter
@Setter
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Local (tenant) dueño del cliente. */
    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 50)
    private String identification;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 1)
    private String dv;

    @Column(name = "legal_organization_id")
    private Integer legalOrganizationId = 13;

    @Column(name = "tribute_id")
    private Integer tributeId = 21;

    @Column(name = "municipality_id")
    private Integer municipalityId;

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
