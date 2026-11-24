package com.sciencebot.pos.voicesales;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"voice-sales.enabled=true", "voice-sales.groq.api-key=not-a-real-key"})
class VoiceSalesBoundaryTest {
    @Autowired WebApplicationContext context;

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void platformUserCannotPreviewASale() throws Exception {
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                .perform(post("/api/v1/voice-sales/preview").contentType("audio/wav").content(new byte[12]))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void openApiDescribesBothBinaryFormatsAndEditablePreview() throws Exception {
        String op = "$.paths['/api/v1/voice-sales/preview'].post";
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                .perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(op + ".requestBody.content['audio/wav']").exists())
                .andExpect(jsonPath(op + ".requestBody.content['audio/webm']").exists())
                .andExpect(jsonPath(op + ".responses['200'].content['application/json'].schema").exists());
    }
}
