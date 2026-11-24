package com.sciencebot.pos.voicesales.internal.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

/** Reads a single binary body, including chunked uploads, without staging a multipart file. */
final class VoiceAudio {
    static final int MAX_BYTES = 5 * 1024 * 1024;

    private VoiceAudio() {}

    static String type(String header) {
        if (header != null) {
            try {
                var type = MediaType.parseMediaType(header);
                if ("audio".equalsIgnoreCase(type.getType())) {
                    if ("webm".equalsIgnoreCase(type.getSubtype())) return "audio/webm";
                    if ("wav".equalsIgnoreCase(type.getSubtype())) return "audio/wav";
                }
            } catch (InvalidMediaTypeException ignored) {
                // Do not return untrusted header contents.
            }
        }
        throw unsupported();
    }

    static byte[] read(InputStream input, String mediaType, long length) {
        if (length > MAX_BYTES) throw tooLarge();
        byte[] bytes;
        try {
            bytes = input.readNBytes(MAX_BYTES + 1);
        } catch (IOException e) {
            throw new PreviewRejected(HttpStatus.BAD_REQUEST, "No se pudo leer el audio.");
        }
        if (bytes.length > MAX_BYTES) {
            Arrays.fill(bytes, (byte) 0);
            throw tooLarge();
        }
        boolean valid = "audio/wav".equals(mediaType)
                ? startsWith(bytes, 0, "RIFF") && startsWith(bytes, 8, "WAVE") && bytes.length > 12
                : isWebm(bytes);
        if (!valid) {
            Arrays.fill(bytes, (byte) 0);
            throw unsupported();
        }
        return bytes;
    }

    private static boolean isWebm(byte[] bytes) {
        if (bytes.length < 12 || bytes[0] != 0x1a || bytes[1] != 0x45
                || (bytes[2] & 0xff) != 0xdf || (bytes[3] & 0xff) != 0xa3) return false;
        // The EBML header of a WebM declares DocType (0x4282), value "webm".
        for (int i = 5; i + 7 <= Math.min(bytes.length, 4096); i++) {
            if (bytes[i] == 0x42 && (bytes[i+1] & 0xff) == 0x82
                    && (bytes[i+2] & 0xff) == 0x84 && startsWith(bytes, i+3, "webm")) return true;
        }
        return false;
    }

    private static boolean startsWith(byte[] bytes, int offset, String marker) {
        byte[] expected = marker.getBytes(StandardCharsets.US_ASCII);
        return bytes.length >= offset + expected.length
                && Arrays.equals(bytes, offset, offset + expected.length, expected, 0, expected.length);
    }

    private static PreviewRejected tooLarge() {
        return new PreviewRejected(HttpStatus.PAYLOAD_TOO_LARGE, "El audio no puede superar 5 MiB.");
    }

    private static PreviewRejected unsupported() {
        return new PreviewRejected(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Envía un archivo de audio WebM o WAV.");
    }
}
