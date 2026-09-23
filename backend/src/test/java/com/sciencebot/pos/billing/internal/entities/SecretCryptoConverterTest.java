package com.sciencebot.pos.billing.internal.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretCryptoConverterTest {

    // 32 bytes en Base64
    private static final String KEY = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";
    private static final String OTHER_KEY = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVowMTIzNDU=";

    private final SecretCryptoConverter converter = new SecretCryptoConverter(KEY);

    @Test
    void encryptsAndDecryptsRoundTrip() {
        String stored = converter.convertToDatabaseColumn("mi-secreto-Ñandú");

        assertTrue(stored.startsWith("v1:"));
        assertFalse(stored.contains("mi-secreto"));
        assertEquals("mi-secreto-Ñandú", converter.convertToEntityAttribute(stored));
    }

    @Test
    void sameSecretProducesDifferentCiphertextEachTime() {
        assertNotEquals(converter.convertToDatabaseColumn("abc"), converter.convertToDatabaseColumn("abc"));
    }

    @Test
    void nullAndEmptyPassThrough() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
        assertEquals("", converter.convertToDatabaseColumn(""));
    }

    @Test
    void decryptingWithAnotherKeyFails() {
        String stored = converter.convertToDatabaseColumn("secreto");
        SecretCryptoConverter other = new SecretCryptoConverter(OTHER_KEY);

        assertThrows(IllegalStateException.class, () -> other.convertToEntityAttribute(stored));
    }

    @Test
    void tamperedValueFails() {
        String stored = converter.convertToDatabaseColumn("secreto");
        String tampered = "v1:" + flipFirstByte(stored.substring(3));

        assertThrows(IllegalStateException.class, () -> converter.convertToEntityAttribute(tampered));
    }

    @Test
    void plainTextInDatabaseIsRejected() {
        assertThrows(IllegalStateException.class, () -> converter.convertToEntityAttribute("texto-en-claro"));
    }

    @Test
    void missingKeyFailsWhenThereIsSomethingToEncrypt() {
        SecretCryptoConverter noKey = new SecretCryptoConverter("");

        assertThrows(IllegalStateException.class, () -> noKey.convertToDatabaseColumn("secreto"));
        assertNull(noKey.convertToDatabaseColumn(null));
    }

    @Test
    void invalidKeyIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> new SecretCryptoConverter("no-es-base64!!").convertToDatabaseColumn("x"));
        // Base64 válido pero de solo 16 bytes
        assertThrows(IllegalStateException.class,
                () -> new SecretCryptoConverter("c29sby0xNi1ieXRlcy1rZXk=").convertToDatabaseColumn("x"));
    }

    /** Cambia el primer byte del payload (el IV) para simular un valor alterado. */
    private static String flipFirstByte(String base64) {
        byte[] bytes = java.util.Base64.getDecoder().decode(base64);
        bytes[0] ^= 0x01;
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }
}
