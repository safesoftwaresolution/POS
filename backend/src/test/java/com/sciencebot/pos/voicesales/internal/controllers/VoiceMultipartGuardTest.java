package com.sciencebot.pos.voicesales.internal.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceMultipartGuardTest {
    private static final String PATH = "/api/v1/voice-sales/preview";
    private final VoiceMultipartGuard guard = new VoiceMultipartGuard();

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH"})
    void multipartIsRejectedForEveryMethodBeforeTheServletCanSpoolIt(String method) throws Exception {
        var chain = run(method, "multipart/form-data; boundary=x", new MockHttpServletResponse());

        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejectionIsPrivateAndUnsupportedMediaType() throws Exception {
        var response = new MockHttpServletResponse();
        run("POST", " MULTIPART/Form-Data; boundary=x", response);

        assertThat(response.getStatus()).isEqualTo(415);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentAsString()).contains("multipart");
    }

    @ParameterizedTest
    @ValueSource(strings = {"audio/wav", "audio/webm;codecs=opus", "application/json"})
    void everythingElseContinuesDownTheChain(String type) throws Exception {
        var response = new MockHttpServletResponse();
        var chain = run("POST", type, response);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void theGuardIsRegisteredForThePreviewAndForEveryAgentEndpoint() {
        var registration = new VoiceUploadConfiguration().voiceMultipartGuard();

        assertThat(registration.getUrlPatterns())
                .containsExactlyInAnyOrder("/api/v1/voice-sales/preview", "/api/v1/voice-sales/agent/*");
    }

    private MockFilterChain run(String method, String contentType, MockHttpServletResponse response) throws Exception {
        var request = new MockHttpServletRequest(method, PATH);
        request.setContentType(contentType);
        var chain = new MockFilterChain();
        guard.doFilter(request, response, chain);
        return chain;
    }
}
