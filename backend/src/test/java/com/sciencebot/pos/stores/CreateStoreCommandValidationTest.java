package com.sciencebot.pos.stores;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CreateStoreCommandValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(CreateStoreCommand command) {
        return validator.validate(command).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void aCompleteStoreIsValid() {
        assertTrue(invalidFields(new CreateStoreCommand("Tienda 1", null, "3001234567", "tienda@test.com",
                "https://tienda.com", "Calle 1", "900123")).isEmpty());
        assertTrue(invalidFields(new CreateStoreCommand("Tienda 1", null, null, "tienda@test.com", null, null, null)).isEmpty());
    }

    @Test
    void nameIsRequiredAndAtMost100Characters() {
        assertEquals(Set.of("name"), invalidFields(new CreateStoreCommand(" ", null, null, "t@test.com", null, null, null)));
        assertEquals(Set.of("name"), invalidFields(new CreateStoreCommand("x".repeat(101), null, null, "t@test.com", null, null, null)));
        assertTrue(invalidFields(new CreateStoreCommand("x".repeat(100), null, null, "t@test.com", null, null, null)).isEmpty());
    }

    @Test
    void phoneIsAtMost20Characters() {
        assertEquals(Set.of("phone"), invalidFields(new CreateStoreCommand("Tienda", null, "1".repeat(21), "t@test.com", null, null, null)));
    }

    @Test
    void emailIsRequiredAndWellFormed() {
        assertEquals(Set.of("email"), invalidFields(new CreateStoreCommand("Tienda", null, null, "no-es-correo", null, null, null)));
        assertEquals(Set.of("email"), invalidFields(new CreateStoreCommand("Tienda", null, null, " ", null, null, null)));
    }

    @Test
    void theMessagesAreInSpanish() {
        String message = validator.validate(new CreateStoreCommand("Tienda", null, null, "no-es-correo", null, null, null))
                .iterator().next().getMessage();
        assertEquals("El correo del local no es válido", message);
    }
}
