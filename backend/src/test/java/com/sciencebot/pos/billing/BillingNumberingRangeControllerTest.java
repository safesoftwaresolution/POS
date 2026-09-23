package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.internal.services.NumberingRangeService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/v1/billing/numbering-ranges/{id}/set-default: solo el administrador del local elige
 * con qué rango factura, y los rechazos del servicio llegan como errores controlados.
 */
@SpringBootTest
class BillingNumberingRangeControllerTest {

    private static final String URL = "/api/v1/billing/numbering-ranges/{id}/set-default";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private NumberingRangeService numberingRangeService;

    // Se arma a mano con springSecurity() para que @WithMockUser llegue a la cadena de filtros.
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void setDefault_AdministratorCanChooseTheRange() throws Exception {
        when(numberingRangeService.setDefaultNumberingRange(20L)).thenReturn(
                new NumberingRangeDto(20L, "01", "SETP", "1876", 1L, 5000L, 1L, null, null, null, true));

        mockMvc.perform(post(URL, 20L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20));

        verify(numberingRangeService).setDefaultNumberingRange(20L);
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void setDefault_SupervisorIsForbidden() throws Exception {
        mockMvc.perform(post(URL, 20L)).andExpect(status().isForbidden());

        verifyNoInteractions(numberingRangeService);
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void setDefault_SellerIsForbidden() throws Exception {
        mockMvc.perform(post(URL, 20L)).andExpect(status().isForbidden());

        verifyNoInteractions(numberingRangeService);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void setDefault_SuperAdminIsForbidden() throws Exception {
        mockMvc.perform(post(URL, 20L)).andExpect(status().isForbidden());

        verifyNoInteractions(numberingRangeService);
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void setDefault_UnknownRangeReturnsNotFound() throws Exception {
        when(numberingRangeService.setDefaultNumberingRange(99L))
                .thenThrow(new EntityNotFoundException("El rango de numeración 99 no existe"));

        mockMvc.perform(post(URL, 99L)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void setDefault_InactiveRangeReturnsConflict() throws Exception {
        when(numberingRangeService.setDefaultNumberingRange(20L))
                .thenThrow(new IllegalStateException("El rango de numeración 20 está inactivo"));

        mockMvc.perform(post(URL, 20L)).andExpect(status().isConflict());
    }
}
