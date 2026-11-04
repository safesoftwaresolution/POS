package com.sciencebot.pos.products.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lote de vencimiento: las unidades que entraron juntas con una misma fecha. {@code quantity} es lo que entró; lo que
 * queda del lote no se guarda, se deriva del stock del producto ({@code ProductLotAllocator}).
 */
@Entity
@Table(name = "product_lots")
@Getter
@Setter
public class ProductLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "lot_code", length = 50)
    private String lotCode;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.receivedAt == null) {
            this.receivedAt = this.createdAt;
        }
    }
}
