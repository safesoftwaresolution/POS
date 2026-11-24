package com.sciencebot.pos.voicesales.internal.groq;

import com.sciencebot.pos.voicesales.internal.ports.VoiceProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GroqVoiceAdapterTest {
    private static final String TRANSCRIBE = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String EXTRACT = "https://api.groq.com/openai/v1/chat/completions";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String INTENT = """
            {"items":[{"mention":"café grande","quantity":2}],"customer":"Ana Pérez",
             "paymentMethod":"Nequi","cashReceived":null}
            """;
    private MockRestServiceServer server;
    private GroqSpeechTranscriber transcriber;
    private GroqSaleIntentExtractor extractor;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var transport = new GroqTransport(builder.build(), "test-key");
        transcriber = new GroqSpeechTranscriber(transport, "whisper-test");
        extractor = new GroqSaleIntentExtractor(transport, "intent-test");
    }

    @Test
    void transcribesOriginalAudioWithSpanishAndDummyFilename() {
        byte[] audio = new byte[] {0, 1, 2, (byte) 255, 32};
        server.expect(requestTo(TRANSCRIBE)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(request -> {
                    assertTrue(request.getHeaders().getContentType().isCompatibleWith(MediaType.MULTIPART_FORM_DATA));
                    byte[] body = ((MockClientHttpRequest) request).getBodyAsBytes();
                    String text = new String(body, StandardCharsets.ISO_8859_1);
                    assertTrue(text.contains("filename=\"audio.webm\""));
                    assertTrue(text.contains("Content-Type: audio/webm"));
                    assertTrue(text.contains("name=\"language\"\r\n"));
                    assertTrue(text.contains("\r\n\r\nes\r\n"));
                    assertTrue(text.contains("whisper-test"));
                    assertTrue(text.contains(new String(audio, StandardCharsets.ISO_8859_1)));
                }).andRespond(withSuccess("{\"text\":\"Dos cafés grandes\"}", MediaType.APPLICATION_JSON));
        assertEquals("Dos cafés grandes", transcriber.transcribe(audio, "audio/webm"));
        server.verify();
    }

    @Test
    void sendsOnlyTranscriptWithStrictSchemaAndReturnsLiteralMentions() {
        server.expect(requestTo(EXTRACT)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(request -> {
                    var body = JSON.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertEquals("intent-test", body.path("model").asString());
                    assertEquals("Dos cafés para Ana. Paga con Nequi", body.path("messages").get(1).path("content").asString());
                    assertEquals("json_schema", body.path("response_format").path("type").asString());
                    var schema = body.path("response_format").path("json_schema");
                    assertTrue(schema.path("strict").asBoolean());
                    assertFalse(schema.path("schema").path("additionalProperties").asBoolean(true));
                    assertFalse(schema.path("schema").path("properties").path("items")
                            .path("items").path("additionalProperties").asBoolean(true));
                    assertEquals(4, schema.path("schema").path("required").size());
                    assertEquals(2, body.path("messages").size());
                    assertFalse(body.toString().contains("productId"));
                }).andRespond(withSuccess(completion(INTENT), MediaType.APPLICATION_JSON));
        var result = extractor.extract("Dos cafés para Ana. Paga con Nequi");
        assertEquals("café grande", result.items().getFirst().mention());
        assertEquals(2, result.items().getFirst().quantity());
        assertEquals("Ana Pérez", result.customer());
        assertEquals("Nequi", result.paymentMethod());
        assertNull(result.cashReceived());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "[]", "not JSON",
            "{\"items\":[],\"customer\":null,\"paymentMethod\":null}",
            "{\"items\":[],\"customer\":null,\"paymentMethod\":null,\"cashReceived\":null,\"storeId\":2}",
            "{\"items\":[{\"mention\":\"agua\",\"quantity\":\"2\"}],\"customer\":null,\"paymentMethod\":null,\"cashReceived\":null}",
            "{\"items\":[{\"mention\":\"agua\",\"quantity\":0}],\"customer\":null,\"paymentMethod\":null,\"cashReceived\":null}",
            "{\"items\":[{\"mention\":\"agua\",\"quantity\":1,\"price\":2}],\"customer\":null,\"paymentMethod\":null,\"cashReceived\":null}",
            "{\"items\":[],\"customer\":null,\"paymentMethod\":null,\"cashReceived\":-1}",
            "{\"items\":[],\"customer\":null,\"paymentMethod\":null,\"cashReceived\":\"50000\"}",
            "{\"items\":[],\"customer\":null,\"paymentMethod\":null,\"cashReceived\":0.001}",
            "{\"items\":[],\"customer\":2,\"paymentMethod\":null,\"cashReceived\":null}",
            "{\"items\":[],\"customer\":null,\"paymentMethod\":null,\"cashReceived\":null} {}"
    })
    void rejectsInvalidStructuredOutputWithoutRetainingCauseOrUpstreamBody(String content) {
        server.expect(requestTo(EXTRACT)).andRespond(withSuccess(completion(content), MediaType.APPLICATION_JSON));
        assertSanitized(() -> extractor.extract("fictitious"));
        server.verify();
    }

    @Test
    void retainsMissingQuantityAndCashAsNullRatherThanInventingThem() {
        String intent = "{\"items\":[{\"mention\":\"agua\",\"quantity\":null}],\"customer\":null,\"paymentMethod\":null,\"cashReceived\":null}";
        server.expect(requestTo(EXTRACT)).andRespond(withSuccess(completion(intent), MediaType.APPLICATION_JSON));
        var result = extractor.extract("agua");
        assertNull(result.items().getFirst().quantity());
        assertNull(result.customer());
        assertNull(result.paymentMethod());
        assertNull(result.cashReceived());
    }

    @Test
    void preservesExactCashAndRejectsOutOfBoundsValuesAndDuplicateFields() {
        String prefix = "{\"items\":[],\"customer\":null,\"paymentMethod\":null,\"cashReceived\":";
        server.expect(requestTo(EXTRACT)).andRespond(withSuccess(
                completion(prefix + "999999999999.99}"), MediaType.APPLICATION_JSON));
        assertEquals(new java.math.BigDecimal("999999999999.99"), extractor.extract("cash").cashReceived());
        server.verify();
        for (String content : new String[] {
                prefix + "1000000000000}",
                prefix + "null,\"cashReceived\":2}",
                prefix.replace("\"customer\":null", "\"customer\":\"" + "a".repeat(201) + "\"") + "null}",
                prefix.replace("[]", "[" + ("{\"mention\":\"agua\",\"quantity\":1},").repeat(30)
                        + "{\"mention\":\"agua\",\"quantity\":1}]") + "null}",
                prefix.replace("[]", "[{\"mention\":\"agua\",\"quantity\":1001}]") + "null}",
                prefix.replace("[]", "[{\"mention\":\"\",\"quantity\":1}]") + "null}"
        }) {
            server.reset();
            server.expect(requestTo(EXTRACT)).andRespond(withSuccess(completion(content), MediaType.APPLICATION_JSON));
            assertSanitized(() -> extractor.extract("fictitious"));
            server.verify();
        }
    }

    @Test
    void rejectsTruncatedCompletionAndMalformedEnvelope() {
        for (String body : new String[] {"null", "[]", "{}", "invalid", "{\"choices\":[]}",
                completion(INTENT).replace("\"stop\"", "\"length\"")}) {
            server.reset();
            server.expect(requestTo(EXTRACT)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertSanitized(() -> extractor.extract("fictitious"));
            server.verify();
        }
    }

    @Test
    void rejectsRateLimitWithoutReadingOrLeakingProviderError() {
        server.expect(requestTo(TRANSCRIBE)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("secret provider body test-key Ana Pérez"));
        assertSanitized(() -> transcriber.transcribe(new byte[] {1}, "audio/wav"));
        server.verify();
    }

    @Test
    void rejectsRedirectInsteadOfFollowingIt() {
        server.expect(requestTo(EXTRACT)).andRespond(withStatus(HttpStatus.TEMPORARY_REDIRECT)
                .location(java.net.URI.create("https://untrusted.invalid/key")));
        assertSanitized(() -> extractor.extract("agua"));
        server.verify();
    }

    @Test
    void convertsTimeoutIntoSanitizedFailure() {
        server.expect(requestTo(EXTRACT)).andRespond(withException(new SocketTimeoutException("sensitive")));
        assertSanitized(() -> extractor.extract("agua"));
        server.verify();
    }

    @Test
    void rejectsInvalidEmptyAndOversizedTranscription() {
        for (String body : new String[] {"{\"text\":2}", "{\"text\":\" \"}",
                "{\"text\":\"" + "a".repeat(12_001) + "\"}"}) {
            server.reset();
            server.expect(requestTo(TRANSCRIBE)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertSanitized(() -> transcriber.transcribe(new byte[] {1}, "audio/wav"));
            server.verify();
        }
    }

    @Test
    void rejectsOversizedProviderBodyAndRefusal() {
        server.expect(requestTo(EXTRACT)).andRespond(withSuccess("a".repeat(65_537), MediaType.APPLICATION_JSON));
        assertSanitized(() -> extractor.extract("agua"));
        server.verify();
        server.reset();
        server.expect(requestTo(EXTRACT)).andRespond(withSuccess(
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"refusal\":\"private\",\"content\":" + JSON.writeValueAsString(INTENT) + "}}]}", MediaType.APPLICATION_JSON));
        assertSanitized(() -> extractor.extract("agua"));
    }

    @Test
    void invalidInputsAndMissingKeyDoNotCallNetwork() {
        assertSanitized(() -> transcriber.transcribe(new byte[] {1}, "text/plain"));
        assertSanitized(() -> transcriber.transcribe(new byte[0], "audio/wav"));
        assertSanitized(() -> transcriber.transcribe(new byte[5 * 1024 * 1024 + 1], "audio/wav"));
        assertSanitized(() -> extractor.extract(" "));
        assertSanitized(() -> extractor.extract("a".repeat(12_001)));
        var missingKey = new GroqTransport(RestClient.builder().build(), "");
        assertSanitized(() -> new GroqSaleIntentExtractor(missingKey, "model").extract("agua"));
        server.verify();
    }

    static String completion(String content) {
        return "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":"
                + JSON.writeValueAsString(content) + "}}]}";
    }

    private static void assertSanitized(org.junit.jupiter.api.function.Executable action) {
        var error = assertThrows(VoiceProviderException.class, action);
        assertEquals("El proveedor de voz no está disponible.", error.getMessage());
        assertNull(error.getCause());
        assertEquals(0, error.getSuppressed().length);
    }
}
