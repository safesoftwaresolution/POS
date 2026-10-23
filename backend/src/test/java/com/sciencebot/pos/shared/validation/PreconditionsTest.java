package com.sciencebot.pos.shared.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreconditionsTest {

    @Nested
    @DisplayName("requireNotNull")
    class RequireNotNullTests {

        @Test
        void returnsValueWhenNotNull() {
            String value = "pos";
            assertThat(Preconditions.requireNotNull(value, "No debe ser nulo")).isEqualTo("pos");
        }

        @Test
        void throwsExceptionWhenNull() {
            assertThatThrownBy(() -> Preconditions.requireNotNull(null, "El valor es nulo"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El valor es nulo");
        }
    }

    @Nested
    @DisplayName("requireNotBlank")
    class RequireNotBlankTests {

        @Test
        void returnsTrimmedValueWhenValid() {
            assertThat(Preconditions.requireNotBlank("  codigo123  ", "Obligatorio"))
                    .isEqualTo("codigo123");
        }

        @Test
        void throwsExceptionWhenNull() {
            assertThatThrownBy(() -> Preconditions.requireNotBlank(null, "Obligatorio"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Obligatorio");
        }

        @Test
        void throwsExceptionWhenEmptyOrWhitespace() {
            assertThatThrownBy(() -> Preconditions.requireNotBlank("   ", "Obligatorio"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Obligatorio");
        }
    }

    @Nested
    @DisplayName("defaultIfBlank")
    class DefaultIfBlankTests {

        @Test
        void returnsTrimmedWhenPresent() {
            assertThat(Preconditions.defaultIfBlank(" 20 ", "30")).isEqualTo("20");
        }

        @Test
        void returnsDefaultWhenNullOrBlank() {
            assertThat(Preconditions.defaultIfBlank(null, "30")).isEqualTo("30");
            assertThat(Preconditions.defaultIfBlank("   ", "30")).isEqualTo("30");
        }
    }

    @Nested
    @DisplayName("requireNotEmpty")
    class RequireNotEmptyTests {

        @Test
        void returnsCollectionWhenNonEmpty() {
            List<String> list = List.of("item1");
            assertThat(Preconditions.requireNotEmpty(list, "No vacio")).hasSize(1);
        }

        @Test
        void throwsWhenCollectionEmptyOrNull() {
            assertThatThrownBy(() -> Preconditions.requireNotEmpty((List<?>) null, "Lista requerida"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Preconditions.requireNotEmpty(Collections.emptyList(), "Lista requerida"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void returnsMapWhenNonEmpty() {
            Map<String, String> map = Map.of("k", "v");
            assertThat(Preconditions.requireNotEmpty(map, "Mapa requerido")).containsKey("k");
        }

        @Test
        void throwsWhenMapEmptyOrNull() {
            assertThatThrownBy(() -> Preconditions.requireNotEmpty((Map<?, ?>) null, "Mapa requerido"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Preconditions.requireNotEmpty(Collections.emptyMap(), "Mapa requerido"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("checkArgument & checkState")
    class ConditionTests {

        @Test
        void checkArgumentPassesWhenTrue() {
            Preconditions.checkArgument(5 > 2, "Error");
        }

        @Test
        void checkArgumentThrowsWhenFalse() {
            assertThatThrownBy(() -> Preconditions.checkArgument(2 > 5, "Argumento no valido"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Argumento no valido");
        }

        @Test
        void checkStatePassesWhenTrue() {
            Preconditions.checkState(true, "Error");
        }

        @Test
        void checkStateThrowsWhenFalse() {
            assertThatThrownBy(() -> Preconditions.checkState(false, "Estado invalido"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Estado invalido");
        }
    }

    @Nested
    @DisplayName("numeric validations")
    class NumericTests {

        @Test
        void requirePositiveValidatesCorrectly() {
            assertThat(Preconditions.requirePositive(10L, "Positivo")).isEqualTo(10L);
            assertThatThrownBy(() -> Preconditions.requirePositive(0, "Positivo"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Preconditions.requirePositive(-5.0, "Positivo"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void requireNonNegativeValidatesCorrectly() {
            assertThat(Preconditions.requireNonNegative(0L, "No negativo")).isEqualTo(0L);
            assertThat(Preconditions.requireNonNegative(10L, "No negativo")).isEqualTo(10L);
            assertThatThrownBy(() -> Preconditions.requireNonNegative(-1, "No negativo"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
