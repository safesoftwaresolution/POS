package com.sciencebot.pos.reports;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/v1/reports/sales-heatmap: solo el administrador (son datos de dinero, como /reports/sales). */
@SpringBootTest
class ReportHeatmapControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administrator_getsTheCells() throws Exception {
        when(reportService.getSalesHeatmap(any(), any())).thenReturn(List.of(
                new SalesHeatmapCellDto(5, 17, 12, new BigDecimal("345000.00"))));

        mockMvc.perform(get("/api/v1/reports/sales-heatmap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayOfWeek").value(5))
                .andExpect(jsonPath("$[0].hourOfDay").value(17))
                .andExpect(jsonPath("$[0].transactionCount").value(12))
                .andExpect(jsonPath("$[0].totalSold").value(345000.00));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/sales-heatmap")).andExpect(status().isForbidden());
        verify(reportService, never()).getSalesHeatmap(any(), any());
    }
}
