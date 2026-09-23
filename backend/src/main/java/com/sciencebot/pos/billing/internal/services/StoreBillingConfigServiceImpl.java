package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingConnectionTestResult;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.FactusApiException;
import com.sciencebot.pos.billing.dto.UpdateBillingCredentialsCommand;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusTokenManager;
import com.sciencebot.pos.billing.internal.entities.FactusCredentials;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.mappers.BillingActivationMapper;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.notifications.CreateNotificationCommand;
import com.sciencebot.pos.notifications.NotificationFacade;
import com.sciencebot.pos.stores.StoreFacade;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class StoreBillingConfigServiceImpl implements StoreBillingConfigService {

    private final StoreBillingConfigRepository repository;
    private final BillingActivationMapper mapper;
    private final FactusTokenManager tokenManager;
    private final NotificationFacade notificationFacade;
    private final StoreFacade storeFacade;

    public StoreBillingConfigServiceImpl(
            StoreBillingConfigRepository repository,
            BillingActivationMapper mapper,
            FactusTokenManager tokenManager,
            @Lazy NotificationFacade notificationFacade,
            @Lazy StoreFacade storeFacade) {
        this.repository = repository;
        this.mapper = mapper;
        this.tokenManager = tokenManager;
        this.notificationFacade = notificationFacade;
        this.storeFacade = storeFacade;
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
    @Transactional(readOnly = true)
    public List<BillingActivationDto> listByStatus(BillingActivationStatus status) {
        Objects.requireNonNull(status, "El estado es obligatorio");
        return repository.findByStatusOrderByUpdatedAtAsc(status).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public BillingActivationDto transition(Long storeId, BillingActivationStatus target, String reason, String actor) {
        return applyTransition(storeId, target, reason, actor, true);
    }

    @Override
    @Transactional
    public BillingActivationDto requestActivation(Long storeId, String actor) {
        Objects.requireNonNull(storeId, "El ID del local es obligatorio");
        if (storeFacade.getDocuments(storeId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Debes subir al menos un documento (RUT, cámara de comercio, etc.) antes de solicitar la activación");
        }
        // El propio local origina esto: no hace falta notificarlo a sí mismo.
        return applyTransition(storeId, BillingActivationStatus.DOCUMENTS_SUBMITTED, null, actor, false);
    }

    private BillingActivationDto applyTransition(Long storeId, BillingActivationStatus target, String reason,
                                                  String actor, boolean notify) {
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
        StoreBillingConfig saved = repository.save(config);

        if (notify) {
            notifyStore(saved, target, reason);
        }
        return mapper.toDto(saved);
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

        config.setUpdatedBy(actor);
        StoreBillingConfig saved = repository.save(config);
        // Las credenciales cambiaron: cualquier token en caché de ese local queda obsoleto.
        tokenManager.invalidate(saved.getStoreId());
        return mapper.toDto(saved);
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

    @Override
    @Transactional(readOnly = true)
    public BillingConnectionTestResult testConnection(Long storeId, BillingEnvironment environment) {
        Objects.requireNonNull(storeId, "El ID del local es obligatorio");
        Objects.requireNonNull(environment, "El entorno es obligatorio");
        try {
            tokenManager.verifyConnection(storeId, environment);
            return BillingConnectionTestResult.success(environment);
        } catch (FactusApiException ex) {
            return BillingConnectionTestResult.failure(environment, ex.getMessage());
        }
    }

    private void notifyStore(StoreBillingConfig config, BillingActivationStatus status, String reason) {
        String title;
        String message;
        switch (status) {
            case AWAITING_PROVIDER -> {
                title = "Documentos de facturación aprobados";
                message = "Tus documentos fueron aprobados. Estamos a la espera de que Factus emita tus credenciales.";
            }
            case ACTIVE -> {
                title = "Facturación electrónica activa";
                message = "Tu facturación electrónica ya está activa. Ya puedes emitir facturas ante la DIAN.";
            }
            case REJECTED -> {
                title = "Solicitud de facturación rechazada";
                message = "Tu solicitud de facturación electrónica fue rechazada: " + reason;
            }
            case SUSPENDED -> {
                title = "Facturación electrónica suspendida";
                message = "Tu facturación electrónica fue suspendida: " + reason;
            }
            default -> {
                return;
            }
        }
        notificationFacade.createNotification(new CreateNotificationCommand(
                config.getStoreId(), "BILLING_STATUS_CHANGED", title, message, "/settings"));
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
