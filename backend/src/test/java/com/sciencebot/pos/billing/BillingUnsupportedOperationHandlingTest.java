package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.internal.services.NumberingRangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un proveedor de facturación que no implementa una operación lanza {@link UnsupportedOperationException}:
 * el controlador de billing debe responder 501 con un mensaje claro, no un 500 genérico.
 * Al ser un {@code @SpringBootTest}, también comprueba que el validador de arranque acepta la
 * configuración por defecto de los tests.
 */
@SpringBootTest
class BillingUnsupportedOperationHandlingTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private NumberingRangeService numberingRangeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void unsupportedOperationFromABillingControllerIsNotImplemented() throws Exception {
        when(numberingRangeService.setDefaultNumberingRange(20L))
                .thenThrow(new UnsupportedOperationException("El proveedor activo no soporta rangos de numeración"));

        mockMvc.perform(post("/api/v1/billing/numbering-ranges/{id}/set-default", 20L))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.error").value("Not Implemented"))
                .andExpect(jsonPath("$.message").value("El proveedor activo no soporta rangos de numeración"));
    }
}
