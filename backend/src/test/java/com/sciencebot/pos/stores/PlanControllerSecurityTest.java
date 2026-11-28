package com.sciencebot.pos.stores;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Plan price editor (SUPER_ADMIN) and the store-side plan catalog (ADMINISTRATOR). */
@SpringBootTest
class PlanControllerSecurityTest {

    private static final String PUT_URL = "/api/v1/backoffice/plans/PRO";
    private static final String BODY = "{\"monthlyPrice\":99000.50,\"yearlyPrice\":990000}";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private StoreFacade storeFacade;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdmin_updatesPrices() throws Exception {
        when(storeFacade.updatePlanPrices(eq("PRO"), any())).thenReturn(
                new PlanDto("PRO", "Pro", 10, 11, new BigDecimal("99000.50"), new BigDecimal("990000")));

        mockMvc.perform(put(PUT_URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PRO"))
                .andExpect(jsonPath("$.monthlyPrice").value(99000.50))
                .andExpect(jsonPath("$.maxActiveUsers").value(11));
        verify(storeFacade).updatePlanPrices("PRO", new UpdatePlanPricesCommand(new BigDecimal("99000.50"), new BigDecimal("990000")));
    }

    @Test
    @WithMockUser(roles = {"ADMINISTRATOR", "SUPERVISOR", "SELLER"})
    void nonSuperAdmin_cannotUpdatePrices() throws Exception {
        mockMvc.perform(put(PUT_URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(storeFacade, never()).updatePlanPrices(any(), any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void invalidPrices_areRefusedWith400() throws Exception {
        for (String body : new String[]{
                "{\"monthlyPrice\":-1}",
                "{\"yearlyPrice\":-1}",
                "{\"monthlyPrice\":1.123}",
                "{\"yearlyPrice\":12345678901}",
                "{\"yearlyPrice\":10.123}",
                "{\"monthlyPrice\":12345678901}"}) {
            mockMvc.perform(put(PUT_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(storeFacade, never()).updatePlanPrices(any(), any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void emptyBody_clearsBothPrices() throws Exception {
        when(storeFacade.updatePlanPrices(eq("PRO"), any())).thenReturn(new PlanDto("PRO", "Pro", 10, 11, null, null));

        mockMvc.perform(put(PUT_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyPrice").doesNotExist());
        verify(storeFacade).updatePlanPrices("PRO", new UpdatePlanPricesCommand(null, null));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administrator_readsTheStorePlanCatalog() throws Exception {
        when(storeFacade.getOwnPlanCatalog()).thenReturn(new StorePlanCatalogDto("BASIC",
                List.of(new PlanDto("BASIC", "Basico", 1, 2, null, null))));

        mockMvc.perform(get("/api/v1/stores/me/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlanCode").value("BASIC"))
                .andExpect(jsonPath("$.plans[0].maxActiveUsers").value(2));
    }

    @Test
    @WithMockUser(roles = {"SELLER", "SUPERVISOR"})
    void otherRoles_cannotReadTheStorePlanCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/stores/me/plans")).andExpect(status().isForbidden());
        verify(storeFacade, never()).getOwnPlanCatalog();
    }

    @Test
    void updatePrices_requiresSuperAdminAtMethodLevel() throws NoSuchMethodException {
        var method = com.sciencebot.pos.stores.internal.controllers.StoreController.class
                .getMethod("updatePlanPrices", String.class, UpdatePlanPricesCommand.class);

        assertEquals("hasRole('SUPER_ADMIN')", method.getAnnotation(PreAuthorize.class).value());
    }
}
