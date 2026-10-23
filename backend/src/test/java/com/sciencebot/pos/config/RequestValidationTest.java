package com.sciencebot.pos.config;

import com.sciencebot.pos.sales.CreateSaleCommand;
import com.sciencebot.pos.sales.CreateSaleItemCommand;
import com.sciencebot.pos.support.CreateTicketCommand;
import com.sciencebot.pos.users.CreateUserCommand;
import com.sciencebot.pos.users.UpdateUserCommand;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprueba que hay un proveedor de Bean Validation en el classpath (spring-boot-starter-validation)
 * y que los DTO de entrada rechazan datos invalidos: sin el starter los @Valid eran decorativos.
 */
class RequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createUser_BlankFieldsAndBadEmailAreRejected() {
        var violations = validator.validate(new CreateUserCommand("", "", "no-es-correo", "", "", null));

        assertTrue(violations.size() >= 5, "se esperaban violaciones de fullName, username, email, password y role");
    }

    @Test
    void createUser_ValidCommandPasses() {
        assertTrue(validator.validate(
                new CreateUserCommand("Juan Perez", "juanp", "juan@tienda.com", "Password123", "SELLER", 1L)).isEmpty());
    }

    @Test
    void updateUser_RequiresNameEmailAndRole() {
        assertFalse(validator.validate(new UpdateUserCommand(null, null, null)).isEmpty());
        assertTrue(validator.validate(new UpdateUserCommand("Ana", "ana@tienda.com", "SELLER")).isEmpty());
    }

    @Test
    void sale_RequiresItemsWithPositiveQuantity() {
        assertFalse(validator.validate(new CreateSaleCommand(null, BigDecimal.TEN, false, List.of(), "CASH")).isEmpty());

        var badItem = new CreateSaleCommand(null, BigDecimal.TEN, false,
                List.of(new CreateSaleItemCommand(5L, 0)), "CASH");
        assertEquals(1, validator.validate(badItem).size());

        var ok = new CreateSaleCommand(null, BigDecimal.TEN, false,
                List.of(new CreateSaleItemCommand(5L, 2)), "CASH");
        assertTrue(validator.validate(ok).isEmpty());
    }

    @Test
    void publicSupportTicket_IsBoundedAndRequiresContent() {
        assertFalse(validator.validate(new CreateTicketCommand(null, null, "", "", null, "x", null, null, null)).isEmpty());

        String tooLong = "a".repeat(4001);
        assertFalse(validator.validate(
                new CreateTicketCommand("PETITION", null, "t", tooLong, null, null, null, null, null)).isEmpty());

        assertTrue(validator.validate(
                new CreateTicketCommand("PETITION", null, "Consulta", "Detalle", "Ana", "ana@x.com", null, 1L, null)).isEmpty());
    }
}
