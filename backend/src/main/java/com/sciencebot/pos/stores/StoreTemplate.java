package com.sciencebot.pos.stores;

/**
 * Functional template a store runs with. It is never stored per store: it is derived from the
 * store's category (see {@code StoreMapper}), and the SUPER_ADMIN edits it on the category.
 */
public enum StoreTemplate {
    /** Default: product sales, inventory, purchases. */
    RETAIL,
    /** Food and beverage businesses. */
    FOOD,
    /** Appointment-based businesses (barbershops, salons, nail studios, boutiques). */
    APPOINTMENTS;

    /** Parses a template key; blank means "not provided" and returns {@code fallback}. */
    public static StoreTemplate parse(String value, StoreTemplate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Plantilla no válida: " + value
                    + ". Valores permitidos: RETAIL, FOOD, APPOINTMENTS.");
        }
    }
}
