package com.sciencebot.pos.suppliers.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "suppliers", uniqueConstraints = {
        @UniqueConstraint(name = "UK_suppliers_store_tax_id", columnNames = {"store_id", "tax_id"})
})
@Getter
@Setter
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Local (tenant) dueño del proveedor. */
    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "tax_id", nullable = false, length = 50)
    private String taxId;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(nullable = false)
    private boolean active = true;

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
