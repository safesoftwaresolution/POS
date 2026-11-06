package com.sciencebot.pos.billing.internal.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Dígito de verificación del NIT (módulo 11 de la DIAN), el mismo que calcula el frontend. */
class DianCheckDigitTest {

    @Test
    void computesTheDianCheckDigitOfKnownNits() {
        assertEquals("4", DianCheckDigit.of("800197268"), "NIT de la DIAN: 800.197.268-4");
        assertEquals("8", DianCheckDigit.of("900123456"));
    }

    @Test
    void remaindersZeroAndOneAreTheDigitItself() {
        assertEquals("0", DianCheckDigit.of("0"), "resto 0");
        assertEquals("1", DianCheckDigit.of("4"), "4 x 3 = 12, resto 1");
        assertEquals("8", DianCheckDigit.of("1"), "1 x 3 = 3, resto 3 -> 11 - 3");
    }

    @Test
    void ignoresSeparatorsLikeTheFrontend() {
        assertEquals("4", DianCheckDigit.of("800.197.268"));
    }

    @Test
    void rejectsAnEmptyOrTooLongNit() {
        assertThrows(IllegalArgumentException.class, () -> DianCheckDigit.of(null));
        assertThrows(IllegalArgumentException.class, () -> DianCheckDigit.of(" - "));
        assertThrows(IllegalArgumentException.class, () -> DianCheckDigit.of("1234567890123456"));
    }

    @Test
    void matchesTellsWhetherTheGivenDigitIsTheRightOne() {
        assertTrue(DianCheckDigit.matches("900123456", "8"));
        assertFalse(DianCheckDigit.matches("900123456", "7"));
        assertFalse(DianCheckDigit.matches("", "8"));
        assertFalse(DianCheckDigit.matches("900123456", null));
    }
}
