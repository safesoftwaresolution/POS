package com.sciencebot.pos.shared.validation;

import java.util.Collection;
import java.util.Map;

/**
 * Utilidad transversal para validación de precondiciones e invariantes del modelo de dominio.
 * <p>
 * Diseñada para evitar repetición de estructuras 'if' en constructores, fábricas y métodos de negocio.
 * Lanza {@link IllegalArgumentException} (para argumentos inválidos) o {@link IllegalStateException}
 * (para estados inconsistentes), que son capturados automáticamente por el manejador global de excepciones.
 */
public final class Preconditions {

    private Preconditions() {
        // Clase de utilidad no instanciable
    }

    /**
     * Valida que el objeto no sea nulo.
     *
     * @param value valor a validar
     * @param message mensaje en caso de error
     * @param <T> tipo del valor
     * @return el mismo valor si no es nulo
     * @throws IllegalArgumentException si el valor es nulo
     */
    public static <T> T requireNotNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * Valida que una cadena de texto no sea nula ni esté compuesta únicamente por espacios en blanco.
     * Retorna la cadena limpia (con {@code .trim()}).
     *
     * @param value cadena a validar
     * @param message mensaje en caso de error
     * @return la cadena sanitizada con {@code .trim()}
     * @throws IllegalArgumentException si la cadena es nula o está en blanco
     */
    public static String requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    /**
     * Retorna el texto sanitizado con trim si tiene contenido real, o un valor por defecto si es nulo/en blanco.
     *
     * @param value cadena a evaluar
     * @param defaultValue valor por defecto a utilizar si value está en blanco
     * @return cadena con trim o valor por defecto
     */
    public static String defaultIfBlank(String value, String defaultValue) {
        return (value != null && !value.isBlank()) ? value.trim() : defaultValue;
    }

    /**
     * Valida que una colección no sea nula ni esté vacía.
     *
     * @param collection colección a validar
     * @param message mensaje en caso de error
     * @param <T> tipo de elementos de la colección
     * @param <C> tipo de la colección
     * @return la colección validada
     * @throws IllegalArgumentException si es nula o vacía
     */
    public static <T, C extends Collection<T>> C requireNotEmpty(C collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /**
     * Valida que un mapa no sea nulo ni esté vacío.
     *
     * @param map mapa a validar
     * @param message mensaje en caso de error
     * @param <M> tipo del mapa
     * @return el mapa validado
     * @throws IllegalArgumentException si es nulo o vacío
     */
    public static <M extends Map<?, ?>> M requireNotEmpty(M map, String message) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return map;
    }

    /**
     * Valida una condición booleana asociada a los argumentos de un método.
     *
     * @param expression condición que debe cumplirse
     * @param message mensaje de error si la condición es falsa
     * @throws IllegalArgumentException si la condición es falsa
     */
    public static void checkArgument(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Valida el estado interno de un objeto o flujo de negocio.
     *
     * @param expression condición de estado que debe ser verdadera
     * @param message mensaje de error si la condición es falsa
     * @throws IllegalStateException si el estado actual es inválido
     */
    public static void checkState(boolean expression, String message) {
        if (!expression) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * Valida que un número sea estrictamente positivo (> 0).
     *
     * @param value número a evaluar
     * @param message mensaje de error
     * @param <T> tipo numérico
     * @return el número validado
     * @throws IllegalArgumentException si el número es nulo o menor o igual a cero
     */
    public static <T extends Number> T requirePositive(T value, String message) {
        requireNotNull(value, message);
        if (value.doubleValue() <= 0.0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * Valida que un número no sea negativo (>= 0).
     *
     * @param value número a evaluar
     * @param message mensaje de error
     * @param <T> tipo numérico
     * @return el número validado
     * @throws IllegalArgumentException si el número es nulo o menor a cero
     */
    public static <T extends Number> T requireNonNegative(T value, String message) {
        requireNotNull(value, message);
        if (value.doubleValue() < 0.0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
