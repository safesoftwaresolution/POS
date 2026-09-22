package com.sciencebot.pos.billing.internal.entities;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra con AES-256-GCM los secretos de facturación (client_secret y password) antes de
 * guardarlos en la base de datos y los descifra al leerlos. La clave viene de
 * {@code billing.encryption-key} (32 bytes en Base64) y no vive en la base de datos.
 *
 * <p>Formato guardado: {@code v1:} + Base64(IV de 12 bytes + texto cifrado + etiqueta GCM).
 * El prefijo permite cambiar el algoritmo o rotar la clave más adelante.
 */
@Converter
class SecretCryptoConverter implements AttributeConverter<String, String> {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final String encodedKey;
    private volatile SecretKey key;

    SecretCryptoConverter(@Value("${billing.encryption-key:}") String encodedKey) {
        this.encodedKey = encodedKey;
    }

    @Override
    public String convertToDatabaseColumn(String plain) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar el secreto de facturación", ex);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || stored.isEmpty()) {
            return stored;
        }
        if (!stored.startsWith(PREFIX)) {
            throw new IllegalStateException("El secreto de facturación guardado no tiene un formato cifrado válido");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "No se pudo descifrar el secreto de facturación (¿cambió billing.encryption-key?)", ex);
        }
    }

    private SecretKey key() {
        SecretKey current = key;
        if (current == null) {
            if (encodedKey == null || encodedKey.isBlank()) {
                throw new IllegalStateException(
                        "Falta la clave de cifrado de facturación (BILLING_ENCRYPTION_KEY)");
            }
            byte[] raw;
            try {
                raw = Base64.getDecoder().decode(encodedKey.trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("BILLING_ENCRYPTION_KEY no es Base64 válido", ex);
            }
            if (raw.length != KEY_BYTES) {
                throw new IllegalStateException("BILLING_ENCRYPTION_KEY debe tener 32 bytes (Base64)");
            }
            current = new SecretKeySpec(raw, "AES");
            key = current;
        }
        return current;
    }
}
