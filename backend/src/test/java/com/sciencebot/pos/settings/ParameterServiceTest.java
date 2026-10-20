package com.sciencebot.pos.settings;

import com.sciencebot.pos.settings.internal.services.ParameterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de integración del motor de catálogos dinámicos: valida entidades, repositorios
 * (consultas derivadas), mapper, servicio, CRUD con borrado lógico y la delegación
 * cross-módulo vía {@link SettingsFacade}.
 *
 * <p>El perfil de test crea el esquema con Hibernate ({@code ddl-auto=create-drop}) y NO
 * ejecuta Flyway, por lo que la prueba siembra sus propios datos en vez de depender del
 * seed de la migración. La migración V6 sobre H2 se valida arrancando el perfil {@code dev}.
 */
@SpringBootTest
@Transactional
class ParameterServiceTest {

    @Autowired
    private ParameterService parameterService;

    @Autowired
    private SettingsFacade settingsFacade;

    private ParameterTopicDto givenTopic(String code) {
        return parameterService.createTopic(new CreateParameterTopicCommand(code, "Tema " + code, "desc"));
    }

    @Test
    void createTopic_normalizesCodeAndIsNotSystem() {
        ParameterTopicDto topic = givenTopic("test_catalog");

        assertEquals("TEST_CATALOG", topic.code()); // normalizado a mayúsculas
        assertFalse(topic.isSystem());
        assertTrue(parameterService.getAllTopics().stream().anyMatch(t -> t.code().equals("TEST_CATALOG")));
    }

    @Test
    void getActiveValuesByTopic_returnsOnlyActiveOrderedBySortOrder() {
        givenTopic("ZONES");
        parameterService.addValue("ZONES", new CreateParameterValueCommand("NATIONAL", "Nacional", "15000", 3));
        parameterService.addValue("ZONES", new CreateParameterValueCommand("LOCAL", "Local", "0", 1));
        parameterService.addValue("ZONES", new CreateParameterValueCommand("METRO", "Metropolitana", "5000", 2));

        List<ParameterValueDto> values = parameterService.getActiveValuesByTopic("ZONES");

        assertEquals(List.of("LOCAL", "METRO", "NATIONAL"),
                values.stream().map(ParameterValueDto::code).toList());
        assertTrue(values.stream().allMatch(ParameterValueDto::active));
        assertTrue(values.stream().allMatch(v -> v.topicCode().equals("ZONES")));
    }

    @Test
    void getValueByTopicAndCode_isCaseInsensitiveAndOptional() {
        givenTopic("PAY");
        parameterService.addValue("PAY", new CreateParameterValueCommand("NEQUI", "Nequi / Daviplata", "42", 1));

        Optional<ParameterValueDto> found = parameterService.getValueByTopicAndCode("pay", "nequi");
        assertTrue(found.isPresent());
        assertEquals("Nequi / Daviplata", found.get().label());

        assertTrue(parameterService.getValueByTopicAndCode("PAY", "NO_EXISTE").isEmpty());
    }

    @Test
    void addValue_thenUpdate_thenDeactivate_fullFlow() {
        givenTopic("FLOW");
        ParameterValueDto value = parameterService.addValue("FLOW",
                new CreateParameterValueCommand("opt_a", "Opción A", "x", 5));
        assertEquals("OPT_A", value.code()); // normalizado
        assertEquals(5, value.sortOrder());
        assertTrue(value.active());

        ParameterValueDto updated = parameterService.updateValue(value.id(),
                new UpdateParameterValueCommand("Opción A modificada", null, 9, null));
        assertEquals("Opción A modificada", updated.label());
        assertEquals(9, updated.sortOrder());
        assertTrue(updated.active()); // active nulo => no se modifica

        // Borrado lógico: desaparece de activos, permanece en la vista de administración.
        parameterService.deactivateValue(value.id());
        assertTrue(parameterService.getActiveValuesByTopic("FLOW").isEmpty());
        List<ParameterValueDto> all = parameterService.getValuesByTopic("FLOW");
        assertEquals(1, all.size());
        assertFalse(all.get(0).active());
    }

    @Test
    void createTopic_duplicateCode_throwsConflict() {
        givenTopic("DUP_TOPIC");
        assertThrows(IllegalStateException.class,
                () -> parameterService.createTopic(new CreateParameterTopicCommand("dup_topic", "Otro", null)));
    }

    @Test
    void addValue_duplicateCodeInTopic_throwsConflict() {
        givenTopic("DUP_VAL");
        parameterService.addValue("DUP_VAL", new CreateParameterValueCommand("CASH", "Efectivo", null, 1));
        assertThrows(IllegalStateException.class,
                () -> parameterService.addValue("DUP_VAL", new CreateParameterValueCommand("cash", "Efectivo 2", null, 2)));
    }

    @Test
    void addValue_unknownTopic_throwsNotFound() {
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> parameterService.addValue("NO_EXISTE", new CreateParameterValueCommand("X", "X", null, 1)));
    }

    @Test
    void updateValue_unknownId_throwsNotFound() {
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> parameterService.updateValue(999_999L, new UpdateParameterValueCommand("x", null, null, null)));
    }

    @Test
    void settingsFacade_delegatesToParameterService() {
        givenTopic("FACADE_TOPIC");
        parameterService.addValue("FACADE_TOPIC", new CreateParameterValueCommand("A", "Alpha", null, 1));

        // La consulta cross-módulo pasa por la fachada pública del módulo settings.
        List<ParameterValueDto> viaFacade = settingsFacade.getActiveValuesByTopic("FACADE_TOPIC");
        assertEquals(1, viaFacade.size());
        assertEquals("A", viaFacade.get(0).code());

        assertTrue(settingsFacade.getValueByTopicAndCode("FACADE_TOPIC", "A").isPresent());
    }
}
