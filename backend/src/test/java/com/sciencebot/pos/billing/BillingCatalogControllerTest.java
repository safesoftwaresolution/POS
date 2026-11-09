package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.DivipolaCatalogDto;
import com.sciencebot.pos.billing.internal.controllers.BillingCatalogController;
import com.sciencebot.pos.billing.internal.services.DivipolaCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Catálogo DIVIPOLA del DANE (departamentos y municipios) que usan el asistente y el cliente fiscal. */
class BillingCatalogControllerTest {

    private static DivipolaCatalog catalog;
    private static BillingCatalogController controller;

    @BeforeAll
    static void load() {
        catalog = new DivipolaCatalog();
        controller = new BillingCatalogController(catalog);
    }

    @Test
    void servesEveryDepartmentAndMunicipalityOfTheDaneCatalog() {
        DivipolaCatalogDto body = controller.getDivipola().getBody();

        assertNotNull(body);
        assertEquals(33, body.departments().size());
        assertEquals(1122, body.municipalities().size());
        assertTrue(body.source().contains("DANE"));
    }

    @Test
    void bogotaHasItsDaneCodeAndName() {
        DivipolaCatalogDto body = controller.getDivipola().getBody();

        DivipolaCatalogDto.Municipality bogota = body.municipalities().stream()
                .filter(m -> "11001".equals(m.code())).findFirst().orElseThrow();
        assertEquals("Bogotá, D.C.", bogota.name());
        assertEquals("11", bogota.departmentCode());
    }

    @Test
    void everyMunicipalityBelongsToAnExistingDepartmentAndStartsWithItsCode() {
        DivipolaCatalogDto body = controller.getDivipola().getBody();
        Set<String> departments = new HashSet<>();
        body.departments().forEach(d -> departments.add(d.code()));

        body.municipalities().forEach(m -> {
            assertTrue(m.code().matches("\\d{5}"), "código de 5 dígitos: " + m.code());
            assertTrue(m.code().startsWith(m.departmentCode()), m.code() + " empieza por " + m.departmentCode());
            assertTrue(departments.contains(m.departmentCode()), "departamento existente: " + m.departmentCode());
        });
    }

    @Test
    void theCatalogIsLoadedOnceAndCannotBeChanged() {
        assertSame(catalog.get(), controller.getDivipola().getBody());
        assertThrows(UnsupportedOperationException.class, () -> catalog.get().departments().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.get().municipalities().clear());
    }

    @Test
    void browsersCanCacheItForADay() {
        ResponseEntity<DivipolaCatalogDto> response = controller.getDivipola();

        assertEquals("max-age=86400", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void anyRoleOfAStoreAndTheSuperAdminCanReadIt() throws NoSuchMethodException {
        var method = BillingCatalogController.class.getMethod("getDivipola");

        assertEquals("/api/v1/billing/catalogs/divipola", method.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("hasAnyRole('SUPER_ADMIN', 'ADMINISTRATOR', 'SUPERVISOR', 'SELLER')",
                method.getAnnotation(PreAuthorize.class).value());
    }
}
