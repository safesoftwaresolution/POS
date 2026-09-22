package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.BillingActivationDto;
import com.sciencebot.pos.billing.BillingActivationStatus;
import com.sciencebot.pos.billing.BillingEnvironment;
import com.sciencebot.pos.billing.UpdateBillingCredentialsCommand;
import com.sciencebot.pos.billing.internal.entities.FactusCredentials;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.mappers.BillingActivationMapper;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class StoreBillingConfigServiceImpl implements StoreBillingConfigService {

    private final StoreBillingConfigRepository repository;
    private final BillingActivationMapper mapper;

    public StoreBillingConfigServiceImpl(StoreBillingConfigRepository repository, BillingActivationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public BillingActivationDto get(Long storeId) {
        Objects.requireNonNull(storeId, "El ID del local es obligatorio");
        return repository.findById(storeId)
                .map(mapper::toDto)
                .orElseGet(() -> mapper.notRequested(storeId));
    }

    @Override
    @Transactional
    public BillingActivationDto transition(Long storeId, BillingActivationStatus target, String reason, String actor) {
        Objects.requireNonNull(target, "El estado destino es obligatorio");
        StoreBillingConfig config = getOrCreate(storeId);
        BillingActivationStatus current = config.getStatus();

        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "No se puede pasar la facturación de " + current + " a " + target);
        }
        if (target.requiresReason() && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("El motivo es obligatorio para pasar a " + target);
        }
        if (target == BillingActivationStatus.ACTIVE
                && !config.credentialsFor(config.getEnvironment()).isComplete()) {
            throw new IllegalStateException(
                    "Faltan credenciales del entorno " + config.getEnvironment() + " para activar la facturación");
        }

        config.setStatus(target);
        config.setStatusReason(target.requiresReason() ? reason.trim() : null);
        config.setUpdatedBy(actor);
        return mapper.toDto(repository.save(config));
    }

    @Override
    @Transactional
    public BillingActivationDto updateCredentials(Long storeId, BillingEnvironment environment,
                                                  UpdateBillingCredentialsCommand command, String actor) {
        Objects.requireNonNull(environment, "El entorno es obligatorio");
        Objects.requireNonNull(command, "Las credenciales son obligatorias");
        StoreBillingConfig config = getOrCreate(storeId);
        FactusCredentials credentials = config.credentialsFor(environment);

        if (command.clientId() != null) {
            credentials.setClientId(requireNotBlank(command.clientId(), "El client_id"));
        }
        if (command.username() != null) {
            credentials.setUsername(requireNotBlank(command.username(), "El usuario"));
        }
        // Vacío significa "conservar el valor guardado": el secreto nunca se devuelve al cliente.
        if (command.clientSecret() != null && !command.clientSecret().isBlank()) {
            credentials.setClientSecret(command.clientSecret().trim());
        }
        if (command.password() != null && !command.password().isBlank()) {
            credentials.setPassword(command.password().trim());
        }
        if (command.numberingRangeId() != null) {
            credentials.setNumberingRangeId(command.numberingRangeId());
        }

        config.setUpdatedBy(actor);
        return mapper.toDto(repository.save(config));
    }

    @Override
    @Transactional
    public BillingActivationDto switchEnvironment(Long storeId, BillingEnvironment environment, String actor) {
        Objects.requireNonNull(environment, "El entorno es obligatorio");
        StoreBillingConfig config = getOrCreate(storeId);

        if (config.getStatus() == BillingActivationStatus.ACTIVE
                && !config.credentialsFor(environment).isComplete()) {
            throw new IllegalStateException(
                    "Faltan credenciales del entorno " + environment + " para cambiar a ese entorno");
        }

        config.setEnvironment(environment);
        config.setUpdatedBy(actor);
        return mapper.toDto(repository.save(config));
    }

    private StoreBillingConfig getOrCreate(Long storeId) {
        Objects.requireNonNull(storeId, "El ID del local es obligatorio");
        return repository.findById(storeId).orElseGet(() -> {
            StoreBillingConfig created = new StoreBillingConfig();
            created.setStoreId(storeId);
            return created;
        });
    }

    private static String requireNotBlank(String value, String label) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " no puede estar vacío");
        }
        return value.trim();
    }
}
