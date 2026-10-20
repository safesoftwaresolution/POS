package com.sciencebot.pos.sales.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales", uniqueConstraints = {
        @UniqueConstraint(name = "UK_sales_invoice_number", columnNames = "invoice_number"),
        @UniqueConstraint(name = "UK_sales_idempotency_key", columnNames = "idempotency_key")
})
@Getter
@Setter
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Local (tenant) donde se registro la venta. */
    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;

    /** Clave de idempotencia (Idempotency-Key). Null = venta sin proteccion de reenvio. */
    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "cash_received", nullable = false, precision = 12, scale = 2)
    private BigDecimal cashReceived;

    @Column(name = "cash_change", nullable = false, precision = 12, scale = 2)
    private BigDecimal cashChange;

    /** Código del valor del tema de parámetros PAYMENT_METHODS (CASH, NEQUI, CARD, TRANSFER...). */
    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod = "CASH";

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SaleItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
