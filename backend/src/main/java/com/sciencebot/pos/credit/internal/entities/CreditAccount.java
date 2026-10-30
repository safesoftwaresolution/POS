package com.sciencebot.pos.credit.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cuenta de fiado de un cliente. {@code balance} solo cambia con las consultas atómicas del repositorio (nunca
 * mutando la entidad), igual que el stock: por eso {@code @DynamicUpdate}, para que guardar otro campo (el cupo)
 * no reescriba un saldo desactualizado.
 */
@Entity
@Table(name = "credit_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "UK_credit_accounts_store_customer", columnNames = {"store_id", "customer_id"})
})
@DynamicUpdate
@Getter
@Setter
public class CreditAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    /** Cupo máximo de deuda; null = sin límite. */
    @Column(name = "credit_limit", precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
