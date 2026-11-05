package com.sciencebot.pos.stores;

/**
 * Visual theme of a store (accent color and icon in the UI). Like {@link StoreTemplate} it is never stored
 * per store: it is derived from the store's category, and the SUPER_ADMIN edits it on the category.
 * The accent colors themselves live in the frontend; this is only the stable key.
 */
public enum StoreTheme {
    /** Brand orange: stores without category, "Otros" and any custom category. */
    DEFAULT,
    GROCERY,
    PHARMACY,
    BAKERY,
    STATIONERY,
    HARDWARE,
    FOOD,
    LIQUOR,
    PETS,
    BEAUTY,
    FASHION;

    /** Parses a theme key; blank means "not provided" and returns {@code fallback}. */
    public static StoreTheme parse(String value, StoreTheme fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tema no válido: " + value + ". Valores permitidos: DEFAULT, GROCERY, PHARMACY, "
                    + "BAKERY, STATIONERY, HARDWARE, FOOD, LIQUOR, PETS, BEAUTY, FASHION.");
        }
    }
}
