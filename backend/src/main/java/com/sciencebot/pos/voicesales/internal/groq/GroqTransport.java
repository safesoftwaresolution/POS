package com.sciencebot.pos.voicesales.internal.groq;

import com.sciencebot.pos.voicesales.internal.ports.VoiceProviderException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** No configurable URLs, redirects, error bodies, retries, logging or persisted provider data. */
final class GroqTransport {
    private static final int MAX_RESPONSE_BYTES = 65_536;
    static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private final RestClient client;
    private final String apiKey;

    GroqTransport(RestClient client, String apiKey) {
        this.client = client;
        this.apiKey = apiKey;
    }

    JsonNode transcribe(Object multipart) {
        return post("https://api.groq.com/openai/v1/audio/transcriptions", MediaType.MULTIPART_FORM_DATA, multipart);
    }

    JsonNode extract(Object payload) {
        return post("https://api.groq.com/openai/v1/chat/completions", MediaType.APPLICATION_JSON, payload);
    }

    private JsonNode post(String endpoint, MediaType contentType, Object payload) {
        try {
            if (apiKey == null || apiKey.isBlank() || apiKey.length() > 512
                    || apiKey.contains("\r") || apiKey.contains("\n")) {
                throw new VoiceProviderException();
            }
            var request = client.post().uri(endpoint).contentType(contentType).accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            if (MediaType.APPLICATION_JSON.equals(contentType)) {
                byte[] json = JSON.writeValueAsBytes(payload);
                // Bypass object converters: their DEBUG output can include the transcript-bearing map.
                request.body(output -> output.write(json));
            } else {
                request.body(payload);
            }
            return request.exchange((sent, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    // Never read provider error bodies, which can echo credentials or personal data.
                    throw new VoiceProviderException();
                }
                byte[] bytes = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length == 0 || bytes.length > MAX_RESPONSE_BYTES) {
                    throw new VoiceProviderException();
                }
                return JSON.readTree(bytes);
            });
        } catch (Exception ignored) {
            // Do not attach the upstream exception: its message may include sensitive data.
            throw new VoiceProviderException();
        }
    }
}
