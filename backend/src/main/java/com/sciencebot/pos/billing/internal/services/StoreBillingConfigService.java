package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingConnectionTestResult;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.UpdateBillingCredentialsCommand;

import java.util.List;

/**
 * Estado y credenciales de facturación electrónica de cada local.
 */
public interface StoreBillingConfigService {

    /** Estado actual. Un local sin fila guardada figura como {@code NOT_REQUESTED}. */
    BillingActivationDto get(Long storeId);

    /** Locales en una etapa dada (para la cola de revisión del back-office). */
    List<BillingActivationDto> listByStatus(BillingActivationStatus status);

    /**
     * Cambia la etapa del proceso. Solo se permiten las transiciones definidas en
     * {@link BillingActivationStatus}; pasar a {@code ACTIVE} exige credenciales completas
     * del entorno activo, y {@code REJECTED} o {@code SUSPENDED} exigen un motivo.
     * Notifica al local del cambio, salvo cuando el propio local lo origina (solicitud inicial).
     */
    BillingActivationDto transition(Long storeId, BillingActivationStatus target, String reason, String actor);

    /**
     * El ADMINISTRATOR del local pide la activación: pasa de {@code NOT_REQUESTED} o
     * {@code REJECTED} a {@code DOCUMENTS_SUBMITTED}. Exige que el local ya tenga al menos un
     * documento subido (RUT, cámara de comercio, etc., ya gestionados por el módulo de locales).
     */
    BillingActivationDto requestActivation(Long storeId, String actor);

    /** Guarda las credenciales de un entorno. Un secret o password vacío conserva el actual. */
    BillingActivationDto updateCredentials(Long storeId, BillingEnvironment environment,
                                           UpdateBillingCredentialsCommand command, String actor);

    /** Cambia el entorno con el que factura el local (sandbox o producción). */
    BillingActivationDto switchEnvironment(Long storeId, BillingEnvironment environment, String actor);

    /**
     * Pide un token a Factus con las credenciales guardadas de un entorno, sin activar nada.
     * Nunca lanza excepción por un rechazo de Factus: el resultado dice si funcionó o no.
     */
    BillingConnectionTestResult testConnection(Long storeId, BillingEnvironment environment);
}
