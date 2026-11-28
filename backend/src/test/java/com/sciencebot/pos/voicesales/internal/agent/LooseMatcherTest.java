package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.settings.ParameterValueDto;
import com.sciencebot.pos.settings.SettingsFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The catalog is the one a real store has; the sentences are what a cashier actually says. */
class LooseMatcherTest {
    private final ProductFacade products = mock(ProductFacade.class);
    private final ServiceFacade services = mock(ServiceFacade.class);
    private final SettingsFacade settings = mock(SettingsFacade.class);

    @BeforeEach
    void setUp() {
        var catalog = List.of(product(1, "Arroz Diana 500g"), product(2, "Aceite Girasol 1L"),
                product(3, "Aceite de Oliva 500ml"), product(4, "Azúcar Blanca 1kg"), product(5, "Azúcar Morena 1kg"),
                product(6, "Coca-Cola 1.5L"), product(7, "Agua Cristal 600ml"));
        when(products.searchProducts(anyString(), isNull(), eq(true), any())).thenAnswer(call -> new PageImpl<>(
                catalog.stream().filter(p -> plain(p.name()).contains(plain(call.getArgument(0)))).toList()));
        when(services.searchServices(anyString(), isNull(), eq(true), any())).thenReturn(new PageImpl<>(List.of()));
        when(settings.getActiveValuesByTopic("PAYMENT_METHODS")).thenReturn(List.of(
                method(1, "CASH", "Efectivo"), method(2, "NEQUI", "Nequi"), method(3, "CARD", "Tarjeta")));
    }

    @Test
    void spokenUnitsAndFillerWordsStillFindTheCatalogName() {
        assertThat(winner("aceite de girasol de un litro")).isEqualTo("Aceite Girasol 1L");
        assertThat(winner("azúcar blanca de un kilo")).isEqualTo("Azúcar Blanca 1kg");
        assertThat(winner("coca cola 1.5 litros")).isEqualTo("Coca-Cola 1.5L");
        assertThat(winner("agua cristal de 600 mililitros")).isEqualTo("Agua Cristal 600ml");
    }

    @Test
    void anAmbiguousMentionReturnsTheClosestOnesInsteadOfGuessing() {
        var outcome = LooseMatcher.items(products, services, "azúcar de un kilo");

        assertThat(outcome.unique()).isFalse();
        assertThat(outcome.best()).extracting(c -> c.label()).containsExactlyInAnyOrder("Azúcar Blanca 1kg",
                "Azúcar Morena 1kg");
    }

    @Test
    void aWordThatSharesNothingFindsNothing() {
        assertThat(LooseMatcher.items(products, services, "pantajado bimbo").best()).isEmpty();
        // One shared word out of four is not enough to call it the same product.
        assertThat(LooseMatcher.items(products, services, "arroz con pollo y papas").best()).isEmpty();
    }

    @Test
    void aMisheardPaymentMethodIsOneTypoAwayFromAConfiguredOne() {
        var outcome = LooseMatcher.paymentMethod(settings, "Neki");
        assertThat(outcome.unique()).isTrue();
        assertThat(outcome.best().getFirst().code()).isEqualTo("NEQUI");

        assertThat(LooseMatcher.paymentMethod(settings, "Nequy").best()).singleElement()
                .satisfies(c -> assertThat(c.code()).isEqualTo("NEQUI"));
        assertThat(LooseMatcher.paymentMethod(settings, "bitcoin").best()).isEmpty();
    }

    private String winner(String mention) {
        var outcome = LooseMatcher.items(products, services, mention);
        assertThat(outcome.unique()).as(mention).isTrue();
        return outcome.best().getFirst().label();
    }

    private static ProductDto product(long id, String name) {
        return new ProductDto(id, "P-" + id, null, name, null, null, null, BigDecimal.ONE, new BigDecimal("1000"),
                10, 0, true, null);
    }

    private static ParameterValueDto method(long id, String code, String label) {
        return new ParameterValueDto(id, "PAYMENT_METHODS", code, label, null, 0, true);
    }

    private static String plain(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
