package com.sciencebot.pos.billing.internal.adapters.factus;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.entities.FactusCredentials;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Decide con qué credenciales y en qué URL de Factus se atiende a un local. Lee solo lo que el
 * super admin cargó para ese local: no existe una cuenta "de la plataforma" a la que recurrir
 * si falta algo, para que un local nunca consuma los documentos de otro.
 */
@Component
public class FactusCredentialsResolver {

    private final StoreBillingConfigRepository repository;
    private final String sandboxUrl;
    private final String productionUrl;

    public FactusCredentialsResolver(
            StoreBillingConfigRepository repository,
            @Value("${factus.url.sandbox:https://api-sandbox.factus.com.co}") String sandboxUrl,
            @Value("${factus.url.production:https://api.factus.com.co}") String productionUrl) {
        this.repository = repository;
        this.sandboxUrl = stripTrailingSlash(sandboxUrl);
        this.productionUrl = stripTrailingSlash(productionUrl);
    }

    /**
     * Credenciales para facturar. Exige que la facturación del local esté {@code ACTIVE}.
     * Si {@code storeId} es nulo se usa el local de la solicitud en curso.
     */
    public FactusAccess resolve(Long storeId) {
        return resolve(storeId, true);
    }

    /**
     * Igual que {@link #resolve(Long)} pero sin exigir {@code ACTIVE}. Sirve para probar la
     * conexión con las credenciales recién cargadas, antes de activar el local. Usa el entorno
     * activo del local.
     */
    public FactusAccess resolveIgnoringStatus(Long storeId) {
        return resolve(storeId, null, false);
    }

    /**
     * Igual que {@link #resolveIgnoringStatus(Long)} pero fija el entorno a probar, aunque no
     * sea el entorno activo del local (el super admin puede querer probar producción antes de
     * cambiarse a ella).
     */
    public FactusAccess resolveIgnoringStatus(Long storeId, BillingEnvironment environment) {
        Objects.requireNonNull(environment, "El entorno es obligatorio");
        return resolve(storeId, environment, false);
    }

    /**
     * El local tiene credenciales completas de Factus para el entorno indicado, sin importar el
     * estado de su facturación. No lanza error si el local no tiene fila: devuelve {@code false}.
     */
    public boolean isConfigured(Long storeId, BillingEnvironment environment) {
        if (storeId == null || environment == null) {
            return false;
        }
        return repository.findById(storeId)
                .map(config -> config.credentialsFor(environment).isComplete())
                .orElse(false);
    }

    private FactusAccess resolve(Long storeId, boolean requireActive) {
        return resolve(storeId, null, requireActive);
    }

    private FactusAccess resolve(Long storeId, BillingEnvironment forcedEnvironment, boolean requireActive) {
        Long effectiveStoreId = storeId != null ? storeId : TenantContext.getStoreId();
        if (effectiveStoreId == null) {
            throw new BillingNotEnabledException("No hay un local activo para facturar electrónicamente");
        }

        StoreBillingConfig config = repository.findById(effectiveStoreId)
                .orElseThrow(() -> new BillingNotEnabledException(
                        "El local no tiene la facturación electrónica habilitada"));

        if (requireActive && config.getStatus() != BillingActivationStatus.ACTIVE) {
            throw new BillingNotEnabledException(
                    "La facturación electrónica del local no está activa (estado: " + config.getStatus() + ")");
        }

        BillingEnvironment environment = forcedEnvironment != null ? forcedEnvironment : config.getEnvironment();
        FactusCredentials credentials = config.credentialsFor(environment);
        if (!credentials.isComplete()) {
            throw new BillingNotEnabledException(
                    "Faltan credenciales de Factus para el entorno " + environment + " del local");
        }

        return new FactusAccess(
                effectiveStoreId,
                environment,
                environment == BillingEnvironment.PRODUCTION ? productionUrl : sandboxUrl,
                credentials.getClientId(),
                credentials.getClientSecret(),
                credentials.getUsername(),
                credentials.getPassword(),
                credentials.getNumberingRangeId()
        );
    }

    private static String stripTrailingSlash(String url) {
        return url == null ? null : url.replaceAll("/+$", "");
    }
}
