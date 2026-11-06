package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingConnectionTestResult;
import com.sciencebot.pos.billing.dto.BillingEnvironment;

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
     * {@link BillingActivationStatus}; {@code SUSPENDED} exige un motivo. Pasar a {@code ACTIVE} (también desde
     * {@code SUSPENDED}) exige, en el entorno del local: la cuenta del proveedor configurada (empresa del local y
     * acceso de la plataforma), la habilitación de la DIAN en {@code AUTHORIZED} y un rango de facturas utilizable;
     * cada pieza que falta tiene su propio mensaje. La habilitación se consulta al proveedor antes de abrir la
     * transacción (no llamar a este método dentro de otra transacción).
     * Notifica al local cuando queda {@code ACTIVE} o {@code SUSPENDED}; pasar a {@code IN_SETUP} no notifica.
     */
    BillingActivationDto transition(Long storeId, BillingActivationStatus target, String reason, String actor);

    /**
     * El asistente de autoservicio empieza (o retoma) la configuración: crea la fila del local si no existe, pasa
     * de {@code NOT_REQUESTED} a {@code IN_SETUP} como {@link #requestActivation} y, mientras el local no esté
     * {@code ACTIVE}, deja su entorno en {@code PRODUCTION} (los locales no usan el sandbox). No notifica.
     */
    BillingActivationDto startSelfServiceSetup(Long storeId, String actor);

    /**
     * El ADMINISTRATOR del local empieza la habilitación: pasa de {@code NOT_REQUESTED} a {@code IN_SETUP}.
     * No pide documentos (Alegra no los necesita). Idempotente si el local ya está en {@code IN_SETUP};
     * un local {@code ACTIVE} o {@code SUSPENDED} no puede volver a configuración por esta vía.
     */
    BillingActivationDto requestActivation(Long storeId, String actor);

    /**
     * Guarda el company.id que el proveedor (Alegra) asignó al local en un entorno. Idempotente:
     * repetir el mismo valor no cambia nada. Descarta cualquier dato en caché del proveedor para el local.
     */
    BillingActivationDto updateProviderCompanyId(Long storeId, BillingEnvironment environment,
                                                 String companyId, String actor);

    /**
     * Cambia el entorno con el que factura el local (sandbox o producción). En cualquier estado, el entorno destino
     * necesita la empresa del local y un rango de facturas utilizable. Si cambia, avisa al local cuál quedó activo.
     */
    BillingActivationDto switchEnvironment(Long storeId, BillingEnvironment environment, String actor);

    /**
     * Pide un token al proveedor con las credenciales guardadas de un entorno, sin activar nada.
     * Nunca lanza excepción por un rechazo del proveedor: el resultado dice si funcionó o no.
     */
    BillingConnectionTestResult testConnection(Long storeId, BillingEnvironment environment);
}
