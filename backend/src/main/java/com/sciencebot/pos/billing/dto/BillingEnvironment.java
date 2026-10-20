package com.sciencebot.pos.billing.dto;

/** Entorno del proveedor de facturacion contra el que trabaja un local. */
public enum BillingEnvironment {
    /** Entorno de pruebas privado. */
    SANDBOX,
    /** Entorno real, con validez ante la DIAN. */
    PRODUCTION
}
