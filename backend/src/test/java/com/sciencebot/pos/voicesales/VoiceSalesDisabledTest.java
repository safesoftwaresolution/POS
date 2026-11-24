package com.sciencebot.pos.voicesales;

import com.sciencebot.pos.voicesales.internal.ports.SaleIntentExtractor;
import com.sciencebot.pos.voicesales.internal.ports.SpeechTranscriber;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class VoiceSalesDisabledTest {
    @Autowired WebApplicationContext context;

    @Test
    @WithMockUser(roles = "SELLER")
    void defaultInstallRequiresNoVoiceProviderAndExposesNoPreview() throws Exception {
        assertThat(context.getBeansOfType(SpeechTranscriber.class)).isEmpty();
        assertThat(context.getBeansOfType(SaleIntentExtractor.class)).isEmpty();
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                .perform(post("/api/v1/voice-sales/preview").contentType("audio/wav").content(new byte[12]))
                .andExpect(status().isNotFound());
    }
}
