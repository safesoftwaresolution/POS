package com.sciencebot.pos.billing.internal.adapters;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Punto único para obtener el proveedor de facturación configurado en {@code billing.provider}.
 * Los servicios que necesitan operar sobre la cuenta del local (habilitación, prueba de conexión,
 * caché) piden el proveedor aquí en lugar de depender de una clase de un proveedor concreto.
 * <p>
 * No hay respaldo silencioso: si el nombre no corresponde a ningún adaptador registrado se lanza
 * {@link IllegalStateException} con los proveedores disponibles.
 */
@Component
public class ActiveBillingProvider {

    private static final String DEFAULT_PROVIDER = "factus";

    private final Map<String, ElectronicInvoicingProvider> providersByName = new TreeMap<>();
    private final String configuredName;

    public ActiveBillingProvider(
            List<ElectronicInvoicingProvider> providers,
            @Value("${billing.provider:factus}") String configuredName) {
        for (ElectronicInvoicingProvider provider : providers) {
            providersByName.putIfAbsent(provider.getProviderName().toLowerCase(), provider);
        }
        this.configuredName = configuredName != null ? configuredName.trim().toLowerCase() : DEFAULT_PROVIDER;
    }

    /** Proveedor configurado en {@code billing.provider}. */
    public ElectronicInvoicingProvider get() {
        ElectronicInvoicingProvider provider = providersByName.get(configuredName);
        if (provider == null) {
            // Sin respaldo silencioso a 'mock': un BILLING_PROVIDER mal escrito emitiría facturas falsas.
            throw new IllegalStateException("Proveedor de facturación '" + configuredName
                    + "' no registrado (billing.provider). Disponibles: " + availableNames());
        }
        return provider;
    }

    /** Hay un adaptador registrado con el nombre configurado. */
    public boolean isConfiguredProviderAvailable() {
        return providersByName.containsKey(configuredName);
    }

    /** Nombre configurado en {@code billing.provider} (en minúsculas, sin espacios). */
    public String configuredName() {
        return configuredName;
    }

    /** Nombres de los adaptadores registrados, en orden alfabético. */
    public Set<String> availableNames() {
        return new TreeSet<>(providersByName.keySet());
    }
}
