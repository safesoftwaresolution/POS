package com.sciencebot.pos.billing.internal.entities;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Durable receipt for the external company creation; never contains platform credentials. */
@Entity
@Table(name = "billing_company_registration", uniqueConstraints = {
        @UniqueConstraint(name = "uk_company_registration_store", columnNames = {"store_id", "environment", "provider_name"}),
        @UniqueConstraint(name = "uk_company_registration_receipt", columnNames = {"environment", "provider_name", "provider_company_id"})
})
@Getter
@Setter
public class BillingCompanyRegistration {

    public enum State { CREATING, UNCERTAIN, RECEIVED, ATTACHED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private BillingEnvironment environment;

    @Column(name = "provider_name", nullable = false, length = 32)
    private String providerName;

    @Column(nullable = false, length = 30)
    private String identification;

    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private State state;

    @Column(name = "provider_company_id", length = 64)
    private String providerCompanyId;

    @Column(name = "processing_started_at", nullable = false)
    private LocalDateTime processingStartedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
