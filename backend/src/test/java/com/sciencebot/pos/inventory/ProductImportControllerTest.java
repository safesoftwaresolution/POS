package com.sciencebot.pos.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** /api/v1/inventory/import: solo administrador y supervisor, y el rechazo "todo o nada" llega como 422 con el detalle. */
@SpringBootTest
class ProductImportControllerTest {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private ProductImportFacade productImportFacade;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void template_isAnXlsxAttachment() throws Exception {
        when(productImportFacade.buildTemplate(true)).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/v1/inventory/import/template").param("withProducts", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"inventario-productos.xlsx\""))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void preview_returnsTheRowsForASupervisor() throws Exception {
        when(productImportFacade.preview(any())).thenReturn(new ProductImportPreviewDto(1, 1, 0, 0, 0,
                List.of(new ProductImportRowDto(2, "P-1", "Arroz", "CREATE", null, 10, List.of(), List.of()))));

        mockMvc.perform(multipart("/api/v1/inventory/import/preview").file(file("productos.xlsx")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toCreate").value(1))
                .andExpect(jsonPath("$.rows[0].action").value("CREATE"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void import_withRowErrors_is422WithThePreview() throws Exception {
        ProductImportPreviewDto preview = new ProductImportPreviewDto(1, 0, 0, 0, 1,
                List.of(new ProductImportRowDto(2, "P-1", null, "ERROR", null, null, List.of("Nombre es obligatorio"), List.of())));
        when(productImportFacade.importProducts(any())).thenThrow(new ProductImportRejectedException(preview));

        mockMvc.perform(multipart("/api/v1/inventory/import").file(file("productos.xlsx")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.message").value("1 fila tiene errores; no se importó ningún producto"))
                .andExpect(jsonPath("$.preview.withErrors").value(1))
                .andExpect(jsonPath("$.preview.rows[0].errors[0]").value("Nombre es obligatorio"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void import_rejectsFilesThatAreNotXlsx() throws Exception {
        mockMvc.perform(multipart("/api/v1/inventory/import").file(file("productos.csv")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(".xlsx")));

        verifyNoInteractions(productImportFacade);
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void seller_isForbiddenEverywhere() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/import/template")).andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/v1/inventory/import/preview").file(file("productos.xlsx"))).andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/v1/inventory/import").file(file("productos.xlsx"))).andExpect(status().isForbidden());

        verifyNoInteractions(productImportFacade);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdmin_isForbidden() throws Exception {
        mockMvc.perform(multipart("/api/v1/inventory/import").file(file("productos.xlsx"))).andExpect(status().isForbidden());

        verifyNoInteractions(productImportFacade);
    }

    private static MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, XLSX, new byte[]{1});
    }
}
