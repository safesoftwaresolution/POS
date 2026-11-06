package com.sciencebot.pos.billing.dto;

import java.util.Set;

/**
 * Etapa de la habilitacion de la facturacion electronica de un local:
 * {@code NOT_REQUESTED -> IN_SETUP -> ACTIVE <-> SUSPENDED} (un local en configuracion tambien puede suspenderse).
 * Es independiente del estado del local en el sistema: un local puede vender sin facturar
 * electronicamente ({@link #NOT_REQUESTED}).
 */
public enum BillingActivationStatus {

    /** El local no ha pedido facturacion electronica. Vende sin factura electronica. */
    NOT_REQUESTED,
    /** El administrador del local empezo la habilitacion y la esta configurando (datos fiscales, DIAN, numeracion). */
    IN_SETUP,
    /** Tiene su empresa en el proveedor y puede emitir facturas electronicas. */
    ACTIVE,
    /** La facturacion fue pausada. Se puede reactivar. */
    SUSPENDED;

    private static final Set<BillingActivationStatus> FROM_NOT_REQUESTED = Set.of(IN_SETUP);
    private static final Set<BillingActivationStatus> FROM_IN_SETUP = Set.of(ACTIVE, SUSPENDED);
    private static final Set<BillingActivationStatus> FROM_ACTIVE = Set.of(SUSPENDED);
    private static final Set<BillingActivationStatus> FROM_SUSPENDED = Set.of(ACTIVE);

    public boolean canTransitionTo(BillingActivationStatus target) {
        Set<BillingActivationStatus> allowed = switch (this) {
            case NOT_REQUESTED -> FROM_NOT_REQUESTED;
            case IN_SETUP -> FROM_IN_SETUP;
            case ACTIVE -> FROM_ACTIVE;
            case SUSPENDED -> FROM_SUSPENDED;
        };
        return allowed.contains(target);
    }

    /** Estados en los que hay que dejar por escrito el motivo. */
    public boolean requiresReason() {
        return this == SUSPENDED;
    }

    /** Nombre para mensajes al usuario (nunca el nombre del enum). */
    public String label() {
        return switch (this) {
            case NOT_REQUESTED -> "no solicitada";
            case IN_SETUP -> "en configuración";
            case ACTIVE -> "activa";
            case SUSPENDED -> "suspendida";
        };
    }
}
