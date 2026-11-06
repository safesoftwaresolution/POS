package com.sciencebot.pos.billing.internal.services;

/**
 * Dígito de verificación del NIT (módulo 11, algoritmo oficial de la DIAN). Es el mismo cálculo que hace el
 * frontend al crear un cliente DIAN ({@code CreateDianCustomerModal.jsx}): se ignoran los separadores, cada dígito
 * (de derecha a izquierda) se multiplica por su peso y el resto 0 o 1 es el propio dígito; si no, 11 menos el resto.
 */
public final class DianCheckDigit {

    private static final int[] WEIGHTS = {3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71};

    private DianCheckDigit() {
    }

    /**
     * Dígito de verificación del NIT.
     *
     * @throws IllegalArgumentException si el NIT no tiene dígitos o tiene más de 15
     */
    public static String of(String nit) {
        String clean = nit == null ? "" : nit.replaceAll("\\D", "");
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("El NIT es obligatorio para calcular el dígito de verificación");
        }
        if (clean.length() > WEIGHTS.length) {
            throw new IllegalArgumentException("El NIT no puede tener más de " + WEIGHTS.length + " dígitos");
        }
        int sum = 0;
        for (int i = 0; i < clean.length(); i++) {
            sum += Character.digit(clean.charAt(clean.length() - 1 - i), 10) * WEIGHTS[i];
        }
        int remainder = sum % 11;
        return String.valueOf(remainder == 0 || remainder == 1 ? remainder : 11 - remainder);
    }

    /** {@code true} si {@code dv} es el dígito de verificación del NIT; un NIT o un dígito vacío nunca coinciden. */
    public static boolean matches(String nit, String dv) {
        if (dv == null || dv.isBlank()) {
            return false;
        }
        try {
            return of(nit).equals(dv.trim());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
