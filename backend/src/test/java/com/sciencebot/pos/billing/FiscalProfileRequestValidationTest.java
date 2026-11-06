package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.DianEnablementRequest;
import com.sciencebot.pos.billing.dto.FiscalProfileRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas de los datos fiscales (RUT) y del TestSetId que el dueño escribe en el asistente. */
class FiscalProfileRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    static FiscalProfileRequest valid() {
        return new FiscalProfileRequest("Tienda La Esquina SAS", "La Esquina", "900123456", "8", 1, "O-13;O-47",
                "4711", "contabilidad@esquina.co", "3001234567", "Calle 10 # 5-20", "05001", "05");
    }

    private Set<String> messages(Object request) {
        return validator.validate(request).stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private static FiscalProfileRequest with(String legalName, String nit, String dv, Integer organizationType,
                                             String regimeCode, String economicActivity, String cityCode,
                                             String departmentCode) {
        FiscalProfileRequest base = valid();
        return new FiscalProfileRequest(legalName, base.tradeName(), nit, dv, organizationType, regimeCode,
                economicActivity, base.email(), base.phone(), base.address(), cityCode, departmentCode);
    }

    @Test
    void aCompleteRutIsValid() {
        assertTrue(messages(valid()).isEmpty(), () -> messages(valid()).toString());
    }

    @Test
    void optionalFieldsMayBeEmpty() {
        FiscalProfileRequest request = new FiscalProfileRequest("Tienda La Esquina SAS", null, "900123456", "8", 2,
                "R-99-PN", null, "a@b.co", null, "Calle 1", "11001", "11");
        assertTrue(messages(request).isEmpty(), () -> messages(request).toString());
        FiscalProfileRequest blankActivity = new FiscalProfileRequest("Tienda La Esquina SAS", "", "900123456", "8",
                2, "R-99-PN", "", "a@b.co", "", "Calle 1", "11001", "11");
        assertTrue(messages(blankActivity).isEmpty(), () -> messages(blankActivity).toString());
    }

    @Test
    void theCheckDigitMustBeTheDianOneOfTheNit() {
        Set<String> errors = messages(with("Tienda La Esquina SAS", "900123456", "7", 1, "O-13", null, "05001", "05"));

        assertEquals(Set.of("El dígito de verificación no corresponde al NIT"), errors);
    }

    @Test
    void nitIsDigitsOnlyBetweenThreeAndFifteen() {
        assertFalse(messages(with("Tienda La Esquina SAS", "900.123.456", "8", 1, "O-13", null, "05001", "05")).isEmpty());
        assertFalse(messages(with("Tienda La Esquina SAS", "12", "0", 1, "O-13", null, "05001", "05")).isEmpty());
        assertFalse(messages(with("Tienda La Esquina SAS", "1234567890123456", "0", 1, "O-13", null, "05001", "05")).isEmpty());
    }

    @Test
    void checkDigitIsOneDigit() {
        Set<String> errors = messages(with("Tienda La Esquina SAS", "900123456", "88", 1, "O-13", null, "05001", "05"));
        assertTrue(errors.contains("El dígito de verificación es un solo número"), errors.toString());
        assertFalse(errors.contains("El dígito de verificación no corresponde al NIT"),
                "con un formato inválido solo se reporta el formato");
    }

    @Test
    void legalNameHasBetweenFiveAnd450Characters() {
        assertFalse(messages(with("SAS", "900123456", "8", 1, "O-13", null, "05001", "05")).isEmpty());
        assertFalse(messages(with("x".repeat(451), "900123456", "8", 1, "O-13", null, "05001", "05")).isEmpty());
    }

    @Test
    void organizationTypeIsOneOrTwo() {
        assertFalse(messages(with("Tienda La Esquina SAS", "900123456", "8", 3, "O-13", null, "05001", "05")).isEmpty());
        assertFalse(messages(with("Tienda La Esquina SAS", "900123456", "8", null, "O-13", null, "05001", "05")).isEmpty());
    }

    @Test
    void regimeCodeIsOneOrMoreKnownResponsibilitiesJoinedBySemicolons() {
        assertTrue(messages(with("Tienda La Esquina SAS", "900123456", "8", 1, "R-99-PN;O-13;O-15;O-23;O-47", null, "05001", "05")).isEmpty());
        assertFalse(messages(with("Tienda La Esquina SAS", "900123456", "8", 1, "O-99", null, "05001", "05")).isEmpty());
        assertFalse(messages(with("Tienda La Esquina SAS", "900123456", "8", 1, "O-13;", null, "05001", "05")).isEmpty());
        assertFalse(messages(with("Tienda La Esquina SAS", "900123456", "8", 1, "", null, "05001", "05")).isEmpty());
    }

    @Test
    void economicActivityIsAFourDigitCiiuCode() {
        assertFalse(messages(with("Tienda La Esquina SAS", "900123456", "8", 1, "O-13", "471", "05001", "05")).isEmpty());
    }

    @Test
    void cityIsFiveDaneDigitsAndTheDepartmentItsFirstTwo() {
        assertFalse(messages(with("Tienda La Esquina SAS", "900123456", "8", 1, "O-13", null, "5001", "05")).isEmpty());
        assertFalse(messages(with("Tienda La Esquina SAS", "900123456", "8", 1, "O-13", null, "05001", "5")).isEmpty());

        Set<String> errors = messages(with("Tienda La Esquina SAS", "900123456", "8", 1, "O-13", null, "05001", "11"));
        assertEquals(Set.of("El departamento no corresponde al municipio (son los dos primeros dígitos de su código DANE)"),
                errors);
    }

    @Test
    void emailAndAddressAreRequired() {
        FiscalProfileRequest request = new FiscalProfileRequest("Tienda La Esquina SAS", null, "900123456", "8", 1,
                "O-13", null, "no-es-correo", null, " ", "05001", "05");
        assertEquals(2, validator.validate(request).size(), () -> messages(request).toString());
    }

    @Test
    void testSetIdMustBeAUuid() {
        assertTrue(messages(new DianEnablementRequest("d7b8f2c4-1a2b-4c3d-9e8f-0a1b2c3d4e5f")).isEmpty());
        assertEquals(Set.of("El código del set de pruebas no tiene el formato correcto"),
                messages(new DianEnablementRequest("1234")));
        assertFalse(messages(new DianEnablementRequest(null)).isEmpty());
    }
}
