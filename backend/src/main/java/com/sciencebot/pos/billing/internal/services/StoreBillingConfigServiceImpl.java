package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingConnectionTestResult;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.exception.BillingProviderException;
import com.sciencebot.pos.billing.internal.adapters.ActiveBillingProvider;
import com.sciencebot.pos.billing.internal.adapters.BillingAccountProvider;
import com.sciencebot.pos.billing.internal.adapters.BillingOnboardingProvider;
import com.sciencebot.pos.billing.internal.adapters.ElectronicInvoicingProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.DianEnablementStatus;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.mappers.BillingActivationMapper;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.notifications.CreateNotificationCommand;
import com.sciencebot.pos.notifications.NotificationFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

@Service
public class StoreBillingConfigServiceImpl implements StoreBillingConfigService {

    private static final Logger log = LoggerFactory.getLogger(StoreBillingConfigServiceImpl.class);

    /** Pestaña de habilitación de la pantalla de facturación: allí el local ve su estado y lo que le falta. */
    static final String ACTIVATION_LINK = "/invoicing?tab=activation";
    private static final String NOTIFICATION_TYPE = "BILLING_STATUS_CHANGED";

    private final StoreBillingConfigRepository repository;
    private final BillingActivationMapper mapper;
    private final ActiveBillingProvider activeBillingProvider;
    private final NotificationFacade notificationFacade;
    private final BillingOnboardingProvider onboardingProvider;
    private final TransactionTemplate transaction;

    public StoreBillingConfigServiceImpl(
            StoreBillingConfigRepository repository,
            BillingActivationMapper mapper,
            ActiveBillingProvider activeBillingProvider,
            @Lazy NotificationFacade notificationFacade,
            BillingOnboardingProvider onboardingProvider,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.activeBillingProvider = activeBillingProvider;
        this.notificationFacade = notificationFacade;
        this.onboardingProvider = onboardingProvider;
        this.transaction = new TransactionTemplate(transactionManager);
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

    /**
     * Sin transacción propia a propósito: pasar a {@code ACTIVE} consulta la habilitación ante la DIAN en el
     * proveedor, y esa llamada nunca se hace dentro de una transacción. Primero se lee la DIAN y después se abre la
     * transacción corta que valida y guarda el cambio.
     */
    @Override
    public BillingActivationDto transition(Long storeId, BillingActivationStatus target, String reason, String actor) {
        Objects.requireNonNull(storeId, "El ID del local es obligatorio");
        Objects.requireNonNull(target, "El estado destino es obligatorio");
        DianEnablementStatus enablement = target == BillingActivationStatus.ACTIVE ? readEnablementFor(storeId) : null;
        return transaction.execute(status -> applyTransition(storeId, target, reason, actor, true, enablement));
    }

    @Override
    @Transactional
    public BillingActivationDto requestActivation(Long storeId, String actor) {
        Objects.requireNonNull(storeId, "El ID del local es obligatorio");
        // Alegra no pide documentos: empezar la habilitación solo deja al local en configuración.
        StoreBillingConfig current = repository.findById(storeId).orElse(null);
        if (current != null && current.getStatus() == BillingActivationStatus.IN_SETUP) {
            return mapper.toDto(current); // ya está en configuración: repetir la solicitud no cambia nada
        }
        // El propio local origina esto: no hace falta notificarlo a sí mismo.
        return applyTransition(storeId, BillingActivationStatus.IN_SETUP, null, actor, false, null);
    }

    @Override
    @Transactional
    public BillingActivationDto startSelfServiceSetup(Long storeId, String actor) {
        Objects.requireNonNull(storeId, "El ID del local es obligatorio");
        BillingActivationStatus status = repository.findById(storeId)
                .map(StoreBillingConfig::getStatus)
                .orElse(BillingActivationStatus.NOT_REQUESTED);
        if (status == BillingActivationStatus.NOT_REQUESTED) {
            requestActivation(storeId, actor);
        }
        StoreBillingConfig config = getOrCreate(storeId);
        // Los locales facturan en producción: el sandbox de Alegra solo factura con el NIT de prueba de la guía.
        // Solo mientras se configura: un local ACTIVE o SUSPENDED cambia de entorno únicamente por el back office,
        // con la guarda y el aviso de switchEnvironment.
        if (config.getStatus() == BillingActivationStatus.IN_SETUP
                && config.getEnvironment() != BillingEnvironment.PRODUCTION) {
            config.setEnvironment(BillingEnvironment.PRODUCTION);
            config.setUpdatedBy(actor);
        }
        return mapper.toDto(repository.save(config));
    }

    /**
     * Habilitación ante la DIAN de la empresa del local en su entorno, leída del proveedor fuera de cualquier
     * transacción. {@code null} si la transición no es válida o la cuenta no está configurada: en ese caso la
     * guarda de {@link #applyTransition} responde con el mensaje que corresponde sin llamar al proveedor.
     */
    private DianEnablementStatus readEnablementFor(Long storeId) {
        StoreBillingConfig config = repository.findById(storeId).orElse(null);
        if (config == null || !config.getStatus().canTransitionTo(BillingActivationStatus.ACTIVE)) {
            return null;
        }
        BillingEnvironment environment = config.getEnvironment();
        if (!activeBillingProvider.get().isAccountConfigured(storeId, environment)) {
            return null;
        }
        try {
            return onboardingProvider.readEnablement(environment, config.companyIdFor(environment));
        } catch (BillingProviderException | BillingNotEnabledException | IllegalArgumentException ex) {
            throw new IllegalStateException("No se pudo confirmar ante la DIAN que el local ya está habilitado: "
                    + ex.getMessage(), ex);
        }
    }

    private BillingActivationDto applyTransition(Long storeId, BillingActivationStatus target, String reason,
                                                 String actor, boolean notify, DianEnablementStatus enablement) {
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
        if (target == BillingActivationStatus.ACTIVE) {
            guardCanInvoice(config, enablement);
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

    /**
     * Para facturar el local necesita, en su entorno: su empresa en el proveedor, la habilitación de la DIAN y un
     * rango de facturas utilizable. Cada pieza que falta tiene su propio mensaje.
     */
    private void guardCanInvoice(StoreBillingConfig config, DianEnablementStatus enablement) {
        BillingEnvironment environment = config.getEnvironment();
        ElectronicInvoicingProvider provider = activeBillingProvider.get();
        requireAccount(config, environment, provider);
        if (enablement == null || enablement.stage() == DianEnablementStatus.Stage.NOT_STARTED) {
            throw new IllegalStateException("El local aún no inicia su habilitación ante la DIAN: falta enviar el "
                    + "código del set de pruebas (TestSetId) que entrega la DIAN.");
        }
        if (!enablement.isAuthorized()) {
            throw new IllegalStateException(enablement.stage() == DianEnablementStatus.Stage.PENDING
                    ? "La DIAN aún no termina de habilitar al local como facturador electrónico. Intenta más tarde."
                    : "La DIAN no ha habilitado al local como facturador electrónico (respuesta: "
                    + enablement.rawValue() + ").");
        }
        if (!provider.hasUsableInvoiceRange(config.getStoreId(), environment)) {
            throw new IllegalStateException("El local no tiene una numeración de facturas vigente en el entorno de "
                    + environment.label() + ". Importa o registra su resolución de la DIAN antes de activar.");
        }
    }

    /** La cuenta del entorno está lista; si no, dice si falta la empresa del local o el acceso de la plataforma. */
    private void requireAccount(StoreBillingConfig config, BillingEnvironment environment,
                                BillingAccountProvider provider) {
        if (provider.isAccountConfigured(config.getStoreId(), environment)) {
            return;
        }
        if (config.companyIdFor(environment) == null) {
            throw new IllegalStateException("El local aún no tiene su empresa registrada en el proveedor de "
                    + "facturación para el entorno de " + environment.label() + ".");
        }
        throw new IllegalStateException("La plataforma aún no tiene acceso al proveedor de facturación en el entorno de "
                + environment.label() + ". Avisa a soporte.");
    }

    @Override
    @Transactional
    public BillingActivationDto updateProviderCompanyId(Long storeId, BillingEnvironment environment,
                                                        String companyId, String actor) {
        Objects.requireNonNull(environment, "El entorno es obligatorio");
        StoreBillingConfig config = getOrCreate(storeId);
        config.assignCompanyId(environment, companyId);
        config.setUpdatedBy(actor);
        StoreBillingConfig saved = repository.save(config);
        activeBillingProvider.get().onAccountChanged(saved.getStoreId());
        return mapper.toDto(saved);
    }

    /**
     * El entorno destino necesita la empresa del local y una numeración de facturas utilizable, sea cual sea el
     * estado (un local en configuración que cambia de entorno también quedaría sin poder facturar). Si el entorno
     * cambia, se avisa al local cuál quedó activo.
     */
    @Override
    @Transactional
    public BillingActivationDto switchEnvironment(Long storeId, BillingEnvironment environment, String actor) {
        Objects.requireNonNull(environment, "El entorno es obligatorio");
        StoreBillingConfig config = getOrCreate(storeId);
        ElectronicInvoicingProvider provider = activeBillingProvider.get();

        requireAccount(config, environment, provider);
        if (!provider.hasUsableInvoiceRange(storeId, environment)) {
            throw new IllegalStateException("El local no tiene una numeración de facturas vigente en el entorno de "
                    + environment.label() + ". Regístrala antes de cambiar a ese entorno.");
        }

        boolean changed = config.getEnvironment() != environment;
        config.setEnvironment(environment);
        config.setUpdatedBy(actor);
        StoreBillingConfig saved = repository.save(config);
        if (changed) {
            notifyEnvironment(saved, environment);
        }
        return mapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BillingConnectionTestResult testConnection(Long storeId, BillingEnvironment environment) {
        Objects.requireNonNull(storeId, "El ID del local es obligatorio");
        Objects.requireNonNull(environment, "El entorno es obligatorio");
        BillingAccountProvider provider = activeBillingProvider.get();
        try {
            provider.verifyConnection(storeId, environment);
            return BillingConnectionTestResult.success(environment, provider.displayName());
        } catch (BillingProviderException ex) {
            return BillingConnectionTestResult.failure(environment, ex.getMessage());
        }
    }

    private void notifyStore(StoreBillingConfig config, BillingActivationStatus status, String reason) {
        String title;
        String message;
        switch (status) {
            case ACTIVE -> {
                title = "Facturación electrónica activa";
                message = "Tu facturación electrónica ya está activa. Ya puedes emitir facturas ante la DIAN.";
            }
            case SUSPENDED -> {
                title = "Facturación electrónica suspendida";
                message = "Tu facturación electrónica fue suspendida: " + reason;
            }
            default -> {
                return; // IN_SETUP lo origina el propio local o el asistente: no hace falta avisar
            }
        }
        notificationFacade.createNotification(new CreateNotificationCommand(
                config.getStoreId(), NOTIFICATION_TYPE, title, message, ACTIVATION_LINK));
    }

    private void notifyEnvironment(StoreBillingConfig config, BillingEnvironment environment) {
        String message = environment == BillingEnvironment.PRODUCTION
                ? "Tu facturación electrónica ahora usa el entorno de producción: las facturas que emitas tienen "
                + "validez ante la DIAN."
                : "Tu facturación electrónica ahora usa el entorno de pruebas: las facturas que emitas son de prueba "
                + "y no tienen validez ante la DIAN.";
        log.info("Local {} factura ahora en {}", config.getStoreId(), environment);
        notificationFacade.createNotification(new CreateNotificationCommand(config.getStoreId(), NOTIFICATION_TYPE,
                "Cambió el entorno de facturación electrónica", message, ACTIVATION_LINK));
    }

    private StoreBillingConfig getOrCreate(Long storeId) {
        Objects.requireNonNull(storeId, "El ID del local es obligatorio");
        return repository.findById(storeId).orElseGet(() -> {
            StoreBillingConfig created = new StoreBillingConfig();
            created.setStoreId(storeId);
            return created;
        });
    }
}
