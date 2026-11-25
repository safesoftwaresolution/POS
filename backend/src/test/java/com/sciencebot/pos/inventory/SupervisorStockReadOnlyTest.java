package com.sciencebot.pos.inventory;

import com.sciencebot.pos.categories.CategoryProductCountProvider;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.purchases.PurchaseFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    // Mocked so that an allowed request can be proven to reach its service, not merely to avoid a 403.
    // The real product service also feeds the category module, so the mock has to keep that interface.
    @MockitoBean(extraInterfaces = CategoryProductCountProvider.class)
    private ProductFacade products;
    @MockitoBean
    private PurchaseFacade purchases;
    @MockitoBean
    private InventoryFacade inventory;
    @MockitoBean
    private ProductImportFacade imports;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_cannotRegisterMovementsOrPurchases() throws Exception {
        forbidden(json(post("/api/v1/inventory/movements"), MOVEMENT));
        forbidden(json(post("/api/v1/purchases"), "{}"));
        verifyNoInteractions(products, purchases, inventory, imports);
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_cannotImportInventory() throws Exception {
        var file = new MockMultipartFile("file", "productos.xlsx", "application/octet-stream", new byte[]{1});

        forbidden(get("/api/v1/inventory/import/template"));
        forbidden(multipart("/api/v1/inventory/import/preview").file(file));
        forbidden(multipart("/api/v1/inventory/import").file(file));
        verifyNoInteractions(products, purchases, inventory, imports);
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_cannotChangeLots() throws Exception {
        forbidden(json(post("/api/v1/products/987654/lots"), LOT));
        forbidden(json(put("/api/v1/products/987654/lots/1"), LOT));
        forbidden(delete("/api/v1/products/987654/lots/1"));
        verifyNoInteractions(products, purchases, inventory, imports);
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_cannotCreateAProductThatAlreadyHasStock() throws Exception {
        forbidden(json(post("/api/v1/products"), "{\"name\":\"Arroz\",\"quantityAvailable\":5}"));
        verifyNoInteractions(products, purchases, inventory, imports);
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisor_canStillAuditStock_andCreateAProductWithoutIt() throws Exception {
        succeeds(get("/api/v1/inventory/movements").param("productId", "987654"), 200);
        succeeds(get("/api/v1/purchases"), 200);
        succeeds(get("/api/v1/products/987654/lots"), 200);
        succeeds(json(post("/api/v1/products"), "{\"name\":\"Arroz\",\"quantityAvailable\":0}"), 201);
        // The POS form omits the field altogether.
        succeeds(json(post("/api/v1/products"), "{\"name\":\"Arroz\"}"), 201);

        verify(inventory).searchMovements(any(), any(), any(), any(), any());
        verify(purchases).searchPurchases(any(), any(), any(), any());
        verify(products).listLots(987654L);
        verify(products, times(2)).createProduct(any());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void administrator_keepsEveryStockOperation() throws Exception {
        succeeds(json(post("/api/v1/inventory/movements"), MOVEMENT), 201);
        succeeds(json(post("/api/v1/purchases"), "{}"), 201);
        succeeds(get("/api/v1/inventory/import/template"), 200);
        succeeds(json(post("/api/v1/products/987654/lots"), LOT), 204);
        succeeds(json(post("/api/v1/products"), "{\"name\":\"Arroz\",\"quantityAvailable\":5}"), 201);

        verify(inventory).registerMovement(anyLong(), any(), anyInt(), any(), any(), any());
        verify(purchases).registerPurchase(any(), any());
        verify(imports).buildTemplate(anyBoolean());
        verify(products).assignLot(anyLong(), anyInt(), any(), any());
        verify(products).createProduct(any());
    }

    private static RequestBuilder json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                       String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private void forbidden(RequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(status().isForbidden());
    }

    private void succeeds(RequestBuilder request, int expected) throws Exception {
        mockMvc.perform(request).andExpect(status().is(expected));
    }
}
