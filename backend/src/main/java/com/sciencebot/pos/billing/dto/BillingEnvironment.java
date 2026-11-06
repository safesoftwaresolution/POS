package com.sciencebot.pos.billing.dto;

/** Entorno del proveedor de facturacion contra el que trabaja un local. */
public enum BillingEnvironment {
    /** Entorno de pruebas privado. */
    SANDBOX("pruebas"),
    /** Entorno real, con validez ante la DIAN. */
    PRODUCTION("producción");

    private final String label;

    BillingEnvironment(String label) {
        this.label = label;
    }

    /** Nombre para mensajes al usuario ("entorno de pruebas", "entorno de producción"). */
    public String label() {
        return label;
    }
}
