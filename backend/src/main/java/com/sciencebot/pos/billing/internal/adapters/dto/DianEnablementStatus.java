package com.sciencebot.pos.billing.internal.adapters.dto;

/**
 * En qué va la habilitación de facturas de una empresa ante la DIAN. {@code rawValue} conserva lo que respondió
 * el proveedor (p. ej. {@code AUTHORIZED}) para mostrarlo o registrarlo tal cual.
 */
public record DianEnablementStatus(Stage stage, String rawValue) {

    public enum Stage {
        /** La DIAN ya habilitó a la empresa: puede facturar en producción. */
        AUTHORIZED,
        /** El set de pruebas se envió y la DIAN aún no responde. */
        PENDING,
        /** El proveedor no reporta ningún estado: no se ha iniciado la habilitación. */
        NOT_STARTED,
        /** Cualquier otro valor del proveedor (ver {@code rawValue}). */
        OTHER
    }

    /** Clasifica el valor del proveedor sin perderlo. */
    public static DianEnablementStatus of(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return new DianEnablementStatus(Stage.NOT_STARTED, rawValue);
        }
        String value = rawValue.trim().toUpperCase();
        Stage stage = switch (value) {
            case "AUTHORIZED" -> Stage.AUTHORIZED;
            case "PENDING" -> Stage.PENDING;
            default -> Stage.OTHER;
        };
        return new DianEnablementStatus(stage, rawValue.trim());
    }

    public boolean isAuthorized() {
        return stage == Stage.AUTHORIZED;
    }
}
