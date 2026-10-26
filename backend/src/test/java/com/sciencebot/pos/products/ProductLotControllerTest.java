package com.sciencebot.pos.products;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/v1/products/{id}/lots: solo administrador y supervisor; el vendedor no puede ni verlos ni tocarlos. */
@SpringBootTest
class ProductLotControllerTest {

    private static final String BODY = "{\"quantity\":5,\"expirationDate\":\"2027-01-15\",\"lotCode\":\"L-1\"}";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void seller_cannotReadOrChangeLots() throws Exception {
        mockMvc.perform(get("/api/v1/products/1/lots")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/products/1/lots").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/products/1/lots/1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/products/1/lots/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_reachesTheEndpoints_andAnUnknownProductIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/products/987654/lots")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/products/987654/lots/1")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void creatingALotWithoutQuantity_isABadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/products/987654/lots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expirationDate\":\"2027-01-15\"}"))
                .andExpect(status().isBadRequest());
    }
}
