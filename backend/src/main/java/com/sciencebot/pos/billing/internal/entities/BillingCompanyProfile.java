package com.sciencebot.pos.billing.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Datos fiscales del local (los de su RUT) con los que se crea y actualiza su empresa en el proveedor de
 * facturación. Uno por local (relación 1 a 1 con stores). Los códigos de ciudad y departamento son los del DANE.
 */
@Entity
@Table(name = "billing_company_profile")
@Getter
@Setter
public class BillingCompanyProfile {

    /** Tipo de identificación DIAN del NIT. */
    public static final String NIT_IDENTIFICATION_TYPE = "31";

    /** Mismo id que el local. */
    @Id
    @Column(name = "store_id")
    private Long storeId;

    /** Razón social tal como figura en el RUT. */
    @Column(name = "legal_name", nullable = false, length = 450)
    private String legalName;

    /** Nombre comercial (opcional). */
    @Column(name = "trade_name", length = 450)
    private String tradeName;

    @Column(name = "nit", nullable = false, length = 30)
    private String nit;

    /** Dígito de verificación del NIT. */
    @Column(name = "dv", nullable = false, length = 1)
    private String dv;

    @Column(name = "identification_type", nullable = false, length = 2)
    private String identificationType = NIT_IDENTIFICATION_TYPE;

    /** Tipo de organización DIAN (1 persona jurídica, 2 persona natural). */
    @Column(name = "organization_type", nullable = false)
    private Integer organizationType;

    /** Régimen o responsabilidad fiscal del local. */
    @Column(name = "regime_code", nullable = false, length = 200)
    private String regimeCode;

    /** Código CIIU de la actividad económica principal (opcional). */
    @Column(name = "economic_activity", length = 4)
    private String economicActivity;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    /** Código DANE del municipio (5 dígitos). */
    @Column(name = "city_code", nullable = false, length = 5)
    private String cityCode;

    /** Código DANE del departamento (2 dígitos). */
    @Column(name = "department_code", nullable = false, length = 2)
    private String departmentCode;

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
