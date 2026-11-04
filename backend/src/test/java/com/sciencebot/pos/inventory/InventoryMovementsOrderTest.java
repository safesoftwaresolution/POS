package com.sciencebot.pos.inventory;

import com.sciencebot.pos.categories.CategoryFacade;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.inventory.internal.controllers.InventoryController;
import com.sciencebot.pos.products.CreateProductCommand;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.users.CreateUserCommand;
import com.sciencebot.pos.users.UserFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GET /api/v1/inventory/movements (kardex) con beans reales contra H2: la primera página tiene que traer lo más
 * reciente. Sin orden explícito la consulta devolvía los movimientos más antiguos primero, y un movimiento recién
 * registrado no aparecía en la pantalla.
 */
@SpringBootTest
class InventoryMovementsOrderTest {

    private static final AtomicLong STORE_IDS = new AtomicLong(8000);

    @Autowired
    private InventoryFacade inventoryFacade;
    @Autowired
    private ProductFacade productFacade;
    @Autowired
    private CategoryFacade categoryFacade;
    @Autowired
    private UserFacade userFacade;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long storeId;
    private String username;
    private Long productId;

    @BeforeEach
    void setUp() {
        storeId = STORE_IDS.incrementAndGet();
        TenantContext.setStoreId(storeId);
        // El kardex guarda quién hizo cada movimiento: hace falta un usuario real (ver ProductImportIntegrationTest).
        username = "kardex-" + storeId;
        userFacade.createUser(new CreateUserCommand("Kardex Tester", username, username + "@test.local",
                "Password123", "ADMINISTRATOR", storeId));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"))));

        Long categoryId = categoryFacade.listAll().stream()
                .filter(c -> c.name().equals("Alimentos y Abarrotes")).findFirst().orElseThrow().id();
        productId = productFacade.createProduct(new CreateProductCommand("K-1", null, "Arroz kardex", categoryId, null,
                BigDecimal.valueOf(2000), BigDecimal.valueOf(3000), 5, 0, null, null, BigDecimal.ZERO, null)).id();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from inventory_movements where store_id = ?", storeId);
        jdbcTemplate.update("delete from products where store_id = ?", storeId);
        jdbcTemplate.update("delete from users where username = ?", username);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void kardex_returnsTheNewestMovementFirst() {
        inventoryFacade.registerMovement(productId, "ENTRY", 10, "Primera");
        inventoryFacade.registerMovement(productId, "SALIDA", 3, "Segunda");
        inventoryFacade.registerMovement(productId, "AJUSTE", 1, "Tercera");

        InventoryController controller = new InventoryController(inventoryFacade);

        Page<InventoryMovementDto> all = controller.searchMovements(productId, null, null, null, 0, 20).getBody();
        assertEquals(List.of("Tercera", "Segunda", "Primera"), all.getContent().stream().map(InventoryMovementDto::reason).toList());

        // Paginado: la primera página trae lo más reciente, no lo más antiguo.
        Page<InventoryMovementDto> firstPage = controller.searchMovements(productId, null, null, null, 0, 2).getBody();
        assertEquals(List.of("Tercera", "Segunda"), firstPage.getContent().stream().map(InventoryMovementDto::reason).toList());
        assertEquals(3, firstPage.getTotalElements());
    }
}
