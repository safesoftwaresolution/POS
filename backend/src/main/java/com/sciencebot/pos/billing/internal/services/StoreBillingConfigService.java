package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.BillingActivationDto;
import com.sciencebot.pos.billing.BillingActivationStatus;
import com.sciencebot.pos.billing.BillingEnvironment;
import com.sciencebot.pos.billing.UpdateBillingCredentialsCommand;

/**
 * Estado y credenciales de facturación electrónica de cada local.
 */
public interface StoreBillingConfigService {

    /** Estado actual. Un local sin fila guardada figura como {@code NOT_REQUESTED}. */
    BillingActivationDto get(Long storeId);

    /**
     * Cambia la etapa del proceso. Solo se permiten las transiciones definidas en
     * {@link BillingActivationStatus}; pasar a {@code ACTIVE} exige credenciales completas
     * del entorno activo, y {@code REJECTED} o {@code SUSPENDED} exigen un motivo.
     */
    BillingActivationDto transition(Long storeId, BillingActivationStatus target, String reason, String actor);

    /** Guarda las credenciales de un entorno. Un secret o password vacío conserva el actual. */
    BillingActivationDto updateCredentials(Long storeId, BillingEnvironment environment,
                                           UpdateBillingCredentialsCommand command, String actor);

    /** Cambia el entorno con el que factura el local (sandbox o producción). */
    BillingActivationDto switchEnvironment(Long storeId, BillingEnvironment environment, String actor);
}
