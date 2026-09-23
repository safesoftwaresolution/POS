package com.sciencebot.pos.billing.dto;

import java.util.Set;

/**
 * Etapa del proceso de habilitacion de la facturacion electronica de un local.
 * Es independiente del estado del local en el sistema: un local puede vender sin facturar
 * electronicamente ({@link #NOT_REQUESTED}).
 */
public enum BillingActivationStatus {

    /** El local no ha pedido facturacion electronica. Vende sin factura electronica. */
    NOT_REQUESTED,
    /** El administrador del local envio sus documentos y espera revision. */
    DOCUMENTS_SUBMITTED,
    /** Los documentos fueron aprobados y enviados al proveedor; se esperan las credenciales. */
    AWAITING_PROVIDER,
    /** Tiene credenciales cargadas y puede emitir facturas electronicas. */
    ACTIVE,
    /** La solicitud fue rechazada. El local puede corregir y volver a enviar. */
    REJECTED,
    /** La facturacion fue pausada. Se puede reactivar. */
    SUSPENDED;

    private static final Set<BillingActivationStatus> FROM_NOT_REQUESTED = Set.of(DOCUMENTS_SUBMITTED);
    private static final Set<BillingActivationStatus> FROM_DOCUMENTS_SUBMITTED = Set.of(AWAITING_PROVIDER, REJECTED);
    private static final Set<BillingActivationStatus> FROM_AWAITING_PROVIDER = Set.of(ACTIVE, REJECTED);
    private static final Set<BillingActivationStatus> FROM_ACTIVE = Set.of(SUSPENDED);
    private static final Set<BillingActivationStatus> FROM_REJECTED = Set.of(DOCUMENTS_SUBMITTED);
    private static final Set<BillingActivationStatus> FROM_SUSPENDED = Set.of(ACTIVE);

    public boolean canTransitionTo(BillingActivationStatus target) {
        Set<BillingActivationStatus> allowed = switch (this) {
            case NOT_REQUESTED -> FROM_NOT_REQUESTED;
            case DOCUMENTS_SUBMITTED -> FROM_DOCUMENTS_SUBMITTED;
            case AWAITING_PROVIDER -> FROM_AWAITING_PROVIDER;
            case ACTIVE -> FROM_ACTIVE;
            case REJECTED -> FROM_REJECTED;
            case SUSPENDED -> FROM_SUSPENDED;
        };
        return allowed.contains(target);
    }

    /** Estados en los que hay que dejar por escrito el motivo. */
    public boolean requiresReason() {
        return this == REJECTED || this == SUSPENDED;
    }
}
