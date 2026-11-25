package com.sciencebot.pos.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A supervisor audits stock (history, purchases, lots) but cannot change it through any endpoint. */
@SpringBootTest
class SupervisorStockReadOnlyTest {

    private static final String MOVEMENT = "{\"productId\":987654,\"movementType\":\"ENTRY\",\"quantity\":5,\"reason\":\"x\"}";
    private static final String LOT = "{\"quantity\":5,\"expirationDate\":\"2027-01-15\",\"lotCode\":\"L-1\"}";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_cannotRegisterMovementsOrPurchases() throws Exception {
        forbidden(json(post("/api/v1/inventory/movements"), MOVEMENT));
        forbidden(json(post("/api/v1/purchases"), "{}"));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_cannotImportInventory() throws Exception {
        var file = new MockMultipartFile("file", "productos.xlsx", "application/octet-stream", new byte[]{1});

        forbidden(get("/api/v1/inventory/import/template"));
        forbidden(multipart("/api/v1/inventory/import/preview").file(file));
        forbidden(multipart("/api/v1/inventory/import").file(file));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_cannotChangeLots() throws Exception {
        forbidden(json(post("/api/v1/products/987654/lots"), LOT));
        forbidden(json(put("/api/v1/products/987654/lots/1"), LOT));
        forbidden(delete("/api/v1/products/987654/lots/1"));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_cannotCreateAProductThatAlreadyHasStock() throws Exception {
        forbidden(json(post("/api/v1/products"), "{\"name\":\"Arroz\",\"quantityAvailable\":5}"));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_canStillAuditStock_andCreateAProductWithoutIt() throws Exception {
        allowed(get("/api/v1/inventory/movements").param("productId", "987654"));
        allowed(get("/api/v1/purchases"));
        // Reaching the service (unknown product) proves the role was accepted.
        mockMvc.perform(get("/api/v1/products/987654/lots")).andExpect(status().isNotFound());
        allowed(json(post("/api/v1/products"), "{\"name\":\"Arroz\",\"quantityAvailable\":0}"));
        // The POS form omits the field altogether.
        allowed(json(post("/api/v1/products"), "{\"name\":\"Arroz\"}"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administrator_keepsEveryStockOperation() throws Exception {
        allowed(json(post("/api/v1/inventory/movements"), MOVEMENT));
        allowed(json(post("/api/v1/purchases"), "{}"));
        allowed(get("/api/v1/inventory/import/template"));
        allowed(json(post("/api/v1/products/987654/lots"), LOT));
        allowed(json(post("/api/v1/products"), "{\"name\":\"Arroz\",\"quantityAvailable\":5}"));
    }

    private static RequestBuilder json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                       String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private void forbidden(RequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(status().isForbidden());
    }

    private void allowed(RequestBuilder request) throws Exception {
        // These calls carry no store or incomplete data, so they may fail later; they must not be refused by role.
        assertThat(mockMvc.perform(request).andReturn().getResponse().getStatus()).isNotEqualTo(403);
    }
}
