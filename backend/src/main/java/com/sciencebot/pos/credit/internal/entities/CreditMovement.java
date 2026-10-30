package com.sciencebot.pos.credit.internal.entities;

import com.sciencebot.pos.credit.CreditMovementType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una línea del libro de la cuenta. Solo se inserta: nunca se edita ni se borra. */
@Entity
@Table(name = "credit_movements")
@Getter
@Setter
public class CreditMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private CreditMovementType type;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "sale_id", updatable = false)
    private Long saleId;

    @Column(name = "payment_method", length = 30, updatable = false)
    private String paymentMethod;

    @Column(name = "due_date", updatable = false)
    private LocalDate dueDate;

    @Column(length = 255, updatable = false)
    private String note;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(length = 50, updatable = false)
    private String username;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
