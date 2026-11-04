package com.sciencebot.pos.billing.internal.entities;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
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

    /** company.id del proveedor (Alegra) por entorno: cada local es una empresa asociada bajo el token de plataforma. */
    @Column(name = "sandbox_company_id", length = 64)
    private String sandboxCompanyId;

    @Column(name = "production_company_id", length = 64)
    private String productionCompanyId;

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

    /** company.id del proveedor para el entorno indicado, o null si aún no se cargó. */
    public String companyIdFor(BillingEnvironment env) {
        return env == BillingEnvironment.PRODUCTION ? productionCompanyId : sandboxCompanyId;
    }

    /** Guarda el company.id del entorno indicado (sin espacios; obligatorio y de hasta 64 caracteres). */
    public void assignCompanyId(BillingEnvironment env, String companyId) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("El company id no puede estar vacío");
        }
        String value = companyId.trim();
        if (value.length() > 64) {
            throw new IllegalArgumentException("El company id no puede superar los 64 caracteres");
        }
        if (env == BillingEnvironment.PRODUCTION) {
            this.productionCompanyId = value;
        } else {
            this.sandboxCompanyId = value;
        }
    }

    @PostLoad
    protected void onLoad() {
        if (this.sandbox == null) this.sandbox = new FactusCredentials();
        if (this.production == null) this.production = new FactusCredentials();
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
