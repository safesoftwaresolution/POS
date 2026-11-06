package com.sciencebot.pos.billing.internal.adapters.alegra;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Decide con qué token, URL y company.id se atiende a un local en Alegra. El token es uno de plataforma por
 * entorno (variable de entorno); el company.id lo carga el SUPER_ADMIN por local y entorno. No hay respaldo a
 * la empresa de otro local: si falta algo, el local no puede facturar.
 */
@Component
@ConditionalOnProperty(name = "billing.provider", havingValue = "alegra")
public class AlegraAccessResolver {

    private final StoreBillingConfigRepository repository;
    private final String sandboxUrl;
    private final String productionUrl;
    private final String sandboxToken;
    private final String productionToken;

    public AlegraAccessResolver(
            StoreBillingConfigRepository repository,
            @Value("${alegra.url.sandbox:https://sandbox-api.alegra.com/e-provider/col/v1}") String sandboxUrl,
            @Value("${alegra.url.production:https://api.alegra.com/e-provider/col/v1}") String productionUrl,
            @Value("${alegra.token.sandbox:}") String sandboxToken,
            @Value("${alegra.token.production:}") String productionToken) {
        this.repository = repository;
        this.sandboxUrl = stripTrailingSlash(sandboxUrl);
        this.productionUrl = stripTrailingSlash(productionUrl);
        this.sandboxToken = sandboxToken;
        this.productionToken = productionToken;
    }

    /** Acceso para facturar. Exige facturación {@code ACTIVE}; sin {@code storeId} usa el local de la solicitud. */
    public AlegraAccess resolve(Long storeId) {
        return resolve(storeId, null, true);
    }

    /** Igual pero sin exigir {@code ACTIVE} y en el entorno indicado: sirve para probar la conexión antes de activar. */
    public AlegraAccess resolveIgnoringStatus(Long storeId, BillingEnvironment environment) {
        Objects.requireNonNull(environment, "El entorno es obligatorio");
        return resolve(storeId, environment, false);
    }

    /** Hay token del entorno y el local tiene company.id cargado, sin importar el estado. Sin fila: {@code false}. */
    public boolean isConfigured(Long storeId, BillingEnvironment environment) {
        if (storeId == null || environment == null || isBlank(tokenFor(environment))) {
            return false;
        }
        return repository.findById(storeId)
                .map(config -> config.companyIdFor(environment) != null)
                .orElse(false);
    }

    /**
     * Acceso de plataforma a un entorno, sin local ni company.id: sirve para dar de alta empresas y consultar la
     * DIAN antes de que el local tenga empresa en Alegra. Sin token del entorno, la plataforma no está lista.
     */
    public AlegraAccess platformAccess(BillingEnvironment environment) {
        Objects.requireNonNull(environment, "El entorno es obligatorio");
        String token = tokenFor(environment);
        if (isBlank(token)) {
            throw new BillingNotEnabledException("La facturación electrónica aún no está disponible en la plataforma "
                    + "(falta el token de Alegra del entorno de " + environment.label() + ")");
        }
        return new AlegraAccess(null, environment, urlFor(environment), token.trim(), null);
    }

    /** Hay token de plataforma para el entorno. */
    public boolean hasPlatformToken(BillingEnvironment environment) {
        return environment != null && !isBlank(tokenFor(environment));
    }

    private String urlFor(BillingEnvironment environment) {
        return environment == BillingEnvironment.PRODUCTION ? productionUrl : sandboxUrl;
    }

    private AlegraAccess resolve(Long storeId, BillingEnvironment forcedEnvironment, boolean requireActive) {
        Long effectiveStoreId = storeId != null ? storeId : TenantContext.getStoreId();
        if (effectiveStoreId == null) {
            throw new BillingNotEnabledException("No hay un local activo para facturar electrónicamente");
        }

        StoreBillingConfig config = repository.findById(effectiveStoreId)
                .orElseThrow(() -> new BillingNotEnabledException(
                        "El local no tiene la facturación electrónica habilitada"));

        if (requireActive && config.getStatus() != BillingActivationStatus.ACTIVE) {
            throw new BillingNotEnabledException(
                    "La facturación electrónica del local no está activa (" + config.getStatus().label() + ")");
        }

        BillingEnvironment environment = forcedEnvironment != null ? forcedEnvironment : config.getEnvironment();
        String token = tokenFor(environment);
        if (isBlank(token)) {
            throw new BillingNotEnabledException(
                    "Falta el token de Alegra del entorno de " + environment.label() + " en la configuración del servidor");
        }
        String companyId = config.companyIdFor(environment);
        if (companyId == null) {
            throw new BillingNotEnabledException(
                    "El local aún no tiene su empresa registrada en Alegra para el entorno de " + environment.label()
                            + " (falta su company.id)");
        }

        return new AlegraAccess(effectiveStoreId, environment,
                environment == BillingEnvironment.PRODUCTION ? productionUrl : sandboxUrl,
                token.trim(), companyId);
    }

    private String tokenFor(BillingEnvironment environment) {
        return environment == BillingEnvironment.PRODUCTION ? productionToken : sandboxToken;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String stripTrailingSlash(String url) {
        return url == null ? null : url.replaceAll("/+$", "");
    }
}
