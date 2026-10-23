package com.sciencebot.pos.billing.internal.adapters;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingProviderException;

/**
 * Puerto de la cuenta de un local ante el proveedor de facturación: permite saber si está lista,
 * probarla y avisar que cambió, sin que los servicios conozcan clases de un proveedor concreto.
 */
public interface BillingAccountProvider {

    /**
     * El local tiene todo lo necesario para operar con este proveedor en el entorno indicado
     * (por ejemplo, credenciales completas). No hace llamadas de red.
     */
    boolean isAccountConfigured(Long storeId, BillingEnvironment environment);

    /**
     * Comprueba contra el proveedor que la cuenta del local en ese entorno funciona, aunque la
     * facturación del local todavía no esté activa.
     *
     * @throws BillingProviderException si el proveedor rechaza la cuenta o no responde
     */
    void verifyConnection(Long storeId, BillingEnvironment environment);

    /** La configuración del local cambió: cualquier dato en caché de ese local queda obsoleto. */
    void onAccountChanged(Long storeId);

    /** Nombre legible del proveedor para mensajes al usuario (ej. "Factus"). */
    String displayName();
}
