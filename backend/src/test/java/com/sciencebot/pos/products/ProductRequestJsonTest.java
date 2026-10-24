package com.sciencebot.pos.products;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

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
    }
}
