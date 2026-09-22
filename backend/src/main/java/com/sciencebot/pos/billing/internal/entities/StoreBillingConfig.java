package com.sciencebot.pos.billing.internal.entities;

import com.sciencebot.pos.billing.BillingActivationStatus;
import com.sciencebot.pos.billing.BillingEnvironment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "store_billing_config")
@Getter
@Setter
public class StoreBillingConfig {

    /** Mismo id que el local (relación 1 a 1 con stores). */
    @Id
    @Column(name = "store_id")
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillingActivationStatus status = BillingActivationStatus.NOT_REQUESTED;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 12)
    private BillingEnvironment environment = BillingEnvironment.SANDBOX;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "clientId", column = @Column(name = "sandbox_client_id", length = 255)),
            @AttributeOverride(name = "clientSecret", column = @Column(name = "sandbox_client_secret", length = 1024)),
            @AttributeOverride(name = "username", column = @Column(name = "sandbox_username", length = 255)),
            @AttributeOverride(name = "password", column = @Column(name = "sandbox_password", length = 1024)),
            @AttributeOverride(name = "numberingRangeId", column = @Column(name = "sandbox_numbering_range_id"))
    })
    private FactusCredentials sandbox = new FactusCredentials();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "clientId", column = @Column(name = "production_client_id", length = 255)),
            @AttributeOverride(name = "clientSecret", column = @Column(name = "production_client_secret", length = 1024)),
            @AttributeOverride(name = "username", column = @Column(name = "production_username", length = 255)),
            @AttributeOverride(name = "password", column = @Column(name = "production_password", length = 1024)),
            @AttributeOverride(name = "numberingRangeId", column = @Column(name = "production_numbering_range_id"))
    })
    private FactusCredentials production = new FactusCredentials();

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Credenciales del entorno indicado. */
    public FactusCredentials credentialsFor(BillingEnvironment env) {
        return env == BillingEnvironment.PRODUCTION ? production : sandbox;
    }

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
