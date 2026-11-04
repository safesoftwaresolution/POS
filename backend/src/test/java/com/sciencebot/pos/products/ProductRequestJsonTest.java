package com.sciencebot.pos.products;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Jackson 3 rechaza por defecto un primitivo ausente ("Cannot map `null` into type `int`"). El formulario de
 * producto no envia `quantityAvailable` (el stock inicial es un movimiento aparte), asi que sin la configuracion
 * de JacksonConfig crear cualquier producto devolvia 400.
 */
@SpringBootTest
class ProductRequestJsonTest {

    @Autowired private JsonMapper mapper;

    @Test
    void createProductBodyFromTheFrontend_ParsesWithMissingPrimitivesDefaultingToZero() {
        String json = "{\"name\":\"Producto de prueba\",\"description\":\"Producto de prueba\",\"internalCode\":\"PROD-001\","
                + "\"barcode\":\"6928819511866\",\"categoryId\":3,\"subcategoryId\":null,\"purchasePrice\":7000,"
                + "\"salePrice\":10000,\"minStock\":5,\"imageUrl\":null}";

        CreateProductCommand command = mapper.readValue(json, CreateProductCommand.class);

        assertEquals("6928819511866", command.barcode());
        assertEquals(0, command.quantityAvailable());
        assertEquals(0, new BigDecimal("10000").compareTo(command.salePrice()));
        assertEquals(5, command.minStock());
        assertNull(command.taxRate());
        assertNull(command.expirationDate(), "la fecha de vencimiento es opcional");
    }

    @Test
    void createProductBody_WithExpirationDate_ParsesTheIsoDate() {
        String json = "{\"name\":\"Leche\",\"internalCode\":\"PROD-002\",\"categoryId\":3,\"purchasePrice\":2000,"
                + "\"salePrice\":3000,\"minStock\":5,\"expirationDate\":\"2026-12-31\"}";

        CreateProductCommand command = mapper.readValue(json, CreateProductCommand.class);

        assertEquals(LocalDate.of(2026, 12, 31), command.expirationDate());
    }

    @Test
    void movementBody_WithExpirationAndLot_ParsesBothAndWithoutThemTheyAreNull() {
        com.sciencebot.pos.inventory.CreateMovementCommand withLot = mapper.readValue(
                "{\"productId\":5,\"movementType\":\"ENTRY\",\"quantity\":50,\"reason\":\"Cargamento\","
                        + "\"expirationDate\":\"2027-01-15\",\"lotCode\":\"L-2210\"}",
                com.sciencebot.pos.inventory.CreateMovementCommand.class);
        com.sciencebot.pos.inventory.CreateMovementCommand without = mapper.readValue(
                "{\"productId\":5,\"movementType\":\"ENTRY\",\"quantity\":50,\"reason\":\"Cargamento\"}",
                com.sciencebot.pos.inventory.CreateMovementCommand.class);

        assertEquals(LocalDate.of(2027, 1, 15), withLot.expirationDate());
        assertEquals("L-2210", withLot.lotCode());
        assertNull(without.expirationDate(), "un cliente que no conoce el vencimiento sigue funcionando");
        assertNull(without.lotCode());
    }

    @Test
    void purchaseItemBody_WithExpiration_ParsesTheLot() {
        com.sciencebot.pos.purchases.CreatePurchaseItemCommand item = mapper.readValue(
                "{\"productId\":5,\"quantity\":100,\"unitCost\":2500,\"expirationDate\":\"2027-02-01\",\"lotCode\":\"A1\"}",
                com.sciencebot.pos.purchases.CreatePurchaseItemCommand.class);

        assertEquals(LocalDate.of(2027, 2, 1), item.expirationDate());
        assertEquals("A1", item.lotCode());
    }
}
