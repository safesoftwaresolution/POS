package com.sciencebot.pos.purchases.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchases", uniqueConstraints = {
        @UniqueConstraint(name = "UK_purchases_idempotency_key", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "UK_purchases_supplier_invoice", columnNames = {"supplier_id", "invoice_number"})
})
@Getter
@Setter
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Local (tenant) donde se registro la compra. */
    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    /** Clave de idempotencia (Idempotency-Key). Null = compra sin proteccion de reenvio. */
    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
