package com.sciencebot.pos.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/v1/finance: solo el administrador (son datos de dinero, como /reports). */
@SpringBootTest
class FinanceControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private FinanceFacade financeFacade;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administrator_getsTheSummary() throws Exception {
        BigDecimal zero = BigDecimal.ZERO;
        when(financeFacade.getSummary(any(), any())).thenReturn(new FinanceSummaryDto(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30), 30,
                new BigDecimal("160000"), zero, zero, zero, zero, zero, new BigDecimal("160000"),
                zero, zero, zero, List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/finance/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value(160000))
                .andExpect(jsonPath("$.days").value(30));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_isForbiddenEverywhere() throws Exception {
        mockMvc.perform(get("/api/v1/finance/summary")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/finance/fixed-costs")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/finance/movements").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"category\":\"Servicios\",\"amount\":100}"))
                .andExpect(status().isForbidden());
        verify(financeFacade, never()).createMovement(any());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void createMovement_withoutCategoryOrType_isABadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/finance/movements").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/finance/movements").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"Servicios\",\"amount\":100}"))
                .andExpect(status().isBadRequest());
        verify(financeFacade, never()).createMovement(any());
    }
}
