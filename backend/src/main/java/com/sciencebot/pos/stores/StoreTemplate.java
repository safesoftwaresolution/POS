package com.sciencebot.pos.stores;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Functional template a store can run with. A store has several: the ones of its category plus the extras the
 * SUPER_ADMIN adds to that store (see {@link #effective}). Nothing is stored per store except those extras.
 */
public enum StoreTemplate {
    /** Default: product sales, inventory, purchases. */
    RETAIL,
    /** Food and beverage businesses. */
    FOOD,
    /** Appointment-based businesses (barbershops, salons, nail studios, boutiques). */
    APPOINTMENTS;

    /** Order used to pick the main template of a set: the most specific one first. */
    private static final List<StoreTemplate> PRIORITY = List.of(APPOINTMENTS, FOOD, RETAIL);

    /** Parses one template key (case-insensitive); the fallback is returned only when the value is blank. */
    public static StoreTemplate parse(String value, StoreTemplate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Plantilla no válida: " + value
                    + ". Valores permitidos: RETAIL, FOOD, APPOINTMENTS.");
        }
    }

    /** Parses a list of keys into a set (no duplicates, enum order). Blank entries are ignored; null is an empty set. */
    public static Set<StoreTemplate> parseAll(Collection<String> values) {
        Set<StoreTemplate> result = EnumSet.noneOf(StoreTemplate.class);
        if (values == null) return result;
        for (String value : values) {
            StoreTemplate parsed = parse(value, null);
            if (parsed != null) result.add(parsed);
        }
        return result;
    }

    /** Templates a store runs with: its category's plus its own extras; RETAIL when there is none at all. */
    public static Set<StoreTemplate> effective(Collection<StoreTemplate> fromCategory, Collection<StoreTemplate> extras) {
        Set<StoreTemplate> result = EnumSet.noneOf(StoreTemplate.class);
        if (fromCategory != null) result.addAll(fromCategory);
        if (extras != null) result.addAll(extras);
        if (result.isEmpty()) result.add(RETAIL);
        return result;
    }

    /** The main template of a set (APPOINTMENTS &gt; FOOD &gt; RETAIL); RETAIL for an empty one. */
    public static StoreTemplate primary(Collection<StoreTemplate> templates) {
        if (templates != null) {
            for (StoreTemplate candidate : PRIORITY) {
                if (templates.contains(candidate)) return candidate;
            }
        }
        return RETAIL;
    }
}
