package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.entities.BillingCompanyRegistration;
import com.sciencebot.pos.billing.internal.entities.BillingCompanyRegistration.State;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.repositories.BillingCompanyRegistrationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** Short, separate transactions around an external CREATE whose idempotency is not guaranteed by the provider. */
@Service
public class BillingCompanyRegistrationTransactions {

    private static final String UNCERTAIN_MESSAGE = "El registro anterior de tu empresa está en curso o su resultado "
            + "no pudo confirmarse. No crearemos otra empresa: contacta al soporte para verificar la asociación.";

    private final BillingCompanyRegistrationRepository repository;
    private final EntityManager entityManager;

    public BillingCompanyRegistrationTransactions(BillingCompanyRegistrationRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    public record Reservation(Long storeId, BillingEnvironment environment, String providerName,
                              String operationId, String providerCompanyId) {
        public boolean hasReceipt() { return providerCompanyId != null; }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(Long storeId, BillingEnvironment environment, String providerName, String identification) {
        // Lock the existing parent, not an absent registration row: this also serializes the first INSERT.
        lockStore(storeId);
        BillingCompanyRegistration registration = repository.findByStoreIdAndEnvironmentAndProviderName(
                storeId, environment, providerName).orElse(null);
        if (registration == null) {
            registration = new BillingCompanyRegistration();
            registration.setStoreId(storeId);
            registration.setEnvironment(environment);
            registration.setProviderName(providerName);
            registration.setIdentification(identification);
        } else if (!Objects.equals(registration.getIdentification(), identification)
                && registration.getState() != State.REJECTED) {
            throw new BillingNotEnabledException("Hay un registro de empresa pendiente para otro NIT. "
                    + "Contacta al soporte antes de cambiar la identificación fiscal.");
        } else if (registration.getProviderCompanyId() != null) {
            return reservation(registration);
        } else if (registration.getState() != State.REJECTED) {
            throw new BillingNotEnabledException(UNCERTAIN_MESSAGE);
        }
        registration.setIdentification(identification);
        registration.setOperationId(UUID.randomUUID().toString());
        registration.setState(State.CREATING);
        registration.setProcessingStartedAt(LocalDateTime.now());
        registration.setUpdatedAt(LocalDateTime.now());
        repository.saveAndFlush(registration);
        return reservation(registration);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public String recoveryMessage(Long storeId, BillingEnvironment environment, String providerName) {
        return repository.findByStoreIdAndEnvironmentAndProviderName(storeId, environment, providerName)
                .filter(registration -> registration.getProviderCompanyId() == null)
                .filter(registration -> registration.getState() == State.CREATING || registration.getState() == State.UNCERTAIN)
                .map(registration -> UNCERTAIN_MESSAGE).orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordReceipt(Reservation reservation, String companyId) {
        if (companyId == null || companyId.isBlank() || companyId.trim().length() > 64) {
            throw new BillingNotEnabledException("El proveedor no devolvió un identificador de empresa válido. "
                    + "Contacta al soporte antes de reintentar.");
        }
        BillingCompanyRegistration registration = current(reservation);
        String normalizedId = companyId.trim();
        if (registration.getProviderCompanyId() != null
                && !normalizedId.equals(registration.getProviderCompanyId())) {
            throw new BillingNotEnabledException("El registro de empresa tiene otra respuesta confirmada; contacta al soporte.");
        }
        if (repository.existsByEnvironmentAndProviderNameAndProviderCompanyIdAndStoreIdNot(
                reservation.environment(), reservation.providerName(), normalizedId, reservation.storeId())
                || assignedToAnotherStore(reservation, normalizedId)) {
            throw new BillingNotEnabledException("La empresa del proveedor ya está asociada a otro local.");
        }
        registration.setProviderCompanyId(normalizedId);
        registration.setState(State.RECEIVED);
        registration.setUpdatedAt(LocalDateTime.now());
        repository.saveAndFlush(registration);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Reservation reservation, boolean confirmedRejection) {
        BillingCompanyRegistration registration = current(reservation);
        if (registration.getProviderCompanyId() == null) {
            registration.setState(confirmedRejection ? State.REJECTED : State.UNCERTAIN);
            registration.setUpdatedAt(LocalDateTime.now());
            repository.saveAndFlush(registration);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAttached(Reservation reservation) {
        BillingCompanyRegistration registration = current(reservation);
        if (registration.getProviderCompanyId() == null) {
            throw new IllegalStateException("Cannot attach a company without a durable provider receipt");
        }
        registration.setState(State.ATTACHED);
        registration.setUpdatedAt(LocalDateTime.now());
        repository.saveAndFlush(registration);
    }

    private BillingCompanyRegistration current(Reservation reservation) {
        lockStore(reservation.storeId());
        BillingCompanyRegistration registration = repository.findByStoreIdAndEnvironmentAndProviderName(
                reservation.storeId(), reservation.environment(), reservation.providerName()).orElseThrow();
        if (!Objects.equals(registration.getOperationId(), reservation.operationId())) {
            throw new BillingNotEnabledException("El registro de empresa cambió mientras se procesaba. Vuelve a consultar.");
        }
        return registration;
    }

    private void lockStore(Long storeId) {
        if (entityManager.find(StoreBillingConfig.class, storeId, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new BillingNotEnabledException("Primero guarda los datos fiscales del local.");
        }
    }

    private boolean assignedToAnotherStore(Reservation reservation, String companyId) {
        String field = reservation.environment() == BillingEnvironment.PRODUCTION
                ? "productionCompanyId" : "sandboxCompanyId";
        return entityManager.createQuery("select count(c) from StoreBillingConfig c where c." + field
                        + " = :companyId and c.storeId <> :storeId", Long.class)
                .setParameter("companyId", companyId).setParameter("storeId", reservation.storeId())
                .getSingleResult() > 0;
    }

    private static Reservation reservation(BillingCompanyRegistration registration) {
        return new Reservation(registration.getStoreId(), registration.getEnvironment(), registration.getProviderName(),
                registration.getOperationId(), registration.getProviderCompanyId());
    }
}
