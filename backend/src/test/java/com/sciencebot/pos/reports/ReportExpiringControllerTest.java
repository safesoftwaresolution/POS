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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** /api/v1/reports/expiring: administrador y supervisor sí, vendedor no. */
@SpringBootTest
class ReportExpiringControllerTest {

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
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_getsTheLotsWithIsoDates() throws Exception {
        when(reportService.getExpiringProductsReport(15)).thenReturn(List.of(
                new ExpiringProductDto(1L, "Leche entera 1L", "P-1", 7L, "L-2210", LocalDate.of(2026, 11, 15), 12, 24, 60, "EXPIRING")));

        mockMvc.perform(get("/api/v1/reports/expiring").param("days", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value("Leche entera 1L"))
                .andExpect(jsonPath("$[0].lotId").value(7))
                .andExpect(jsonPath("$[0].lotCode").value("L-2210"))
                .andExpect(jsonPath("$[0].expirationDate").value("2026-11-15"))
                .andExpect(jsonPath("$[0].daysToExpire").value(12))
                .andExpect(jsonPath("$[0].lotRemaining").value(24))
                .andExpect(jsonPath("$[0].quantityAvailable").value(60))
                .andExpect(jsonPath("$[0].status").value("EXPIRING"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administrator_withoutDays_usesTheServiceDefault() throws Exception {
        when(reportService.getExpiringProductsReport(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reports/expiring"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void seller_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/expiring"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reportService);
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_downloadsThePdf() throws Exception {
        when(reportService.getExpiringProductsReportPdf(15)).thenReturn("%PDF-1.4".getBytes());

        mockMvc.perform(get("/api/v1/reports/expiring/pdf").param("days", "15"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"vencimientos.pdf\""))
                .andExpect(content().bytes("%PDF-1.4".getBytes()));
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void seller_cannotDownloadThePdf() throws Exception {
        mockMvc.perform(get("/api/v1/reports/expiring/pdf"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reportService);
    }
}
