package com.sciencebot.pos.voicesales.internal.controllers;

import com.sciencebot.pos.voicesales.VoiceAgentFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VoiceAgentControllerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new VoiceAgentController(mock(VoiceAgentFacade.class))).build();
    }

    @Test
    void aBodyThatIsNotValidUtf8JsonIsABadRequestNotAServerError() throws Exception {
        byte[] latin1 = "{\"text\":\"azúcar\"}".getBytes(StandardCharsets.ISO_8859_1);

        var body = mvc.perform(post("/api/v1/voice-sales/agent/turn").contentType(MediaType.APPLICATION_JSON)
                        .content(latin1))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("La solicitud no es válida.").doesNotContain("az");
    }

    @Test
    void malformedJsonIsABadRequestToo() throws Exception {
        mvc.perform(post("/api/v1/voice-sales/agent/turn").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest());
    }
}
