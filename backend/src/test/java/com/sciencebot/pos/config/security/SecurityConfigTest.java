package com.sciencebot.pos.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El frontend solo dispara el refresh automatico de sesion ante un 401 (ver
 * fetchApi en frontend/src/services/api.js). Sin un AuthenticationEntryPoint
 * explicito, Spring Security cae en Http403ForbiddenEntryPoint por defecto y
 * un token ausente/expirado responde 403 en vez de 401, dejando al usuario
 * sin poder renovar sesion cuando el access token expira.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedEndpoint_NoToken_ReturnsUnauthorizedNotForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_InvalidToken_ReturnsUnauthorizedNotForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }
}
