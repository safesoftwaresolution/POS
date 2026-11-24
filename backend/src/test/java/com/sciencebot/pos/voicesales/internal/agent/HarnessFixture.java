package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.services.ServiceDto;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.settings.ParameterValueDto;
import com.sciencebot.pos.settings.SettingsFacade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * One small store shared by the scripted harness and the live evaluation. Searches match on a normalized
 * "name contains query", like the real catalog search, so a live model that spells things slightly differently
 * still gets a realistic answer.
 */
final class HarnessFixture {
    static final long COFFEE = 11, WATER = 12, COLA = 21, LEMON = 22, HAIRCUT = 31;
    static final long GENERAL = 1, ANA_1 = 41, ANA_2 = 42, LUIS = 43;

    private static final List<ProductDto> PRODUCTS = List.of(
            product(COFFEE, "Café grande", "3500", 40), product(WATER, "Agua", "2000", 2),
            product(COLA, "Gaseosa cola", "3000", 20), product(LEMON, "Gaseosa limón", "3000", 20));
    private static final List<ServiceDto> SERVICES = List.of(
            new ServiceDto(HAIRCUT, "S-31", "Corte", null, null, null, new BigDecimal("15000"),
                    BigDecimal.ZERO, null, "94", null, true));
    private static final List<CustomerDto> CUSTOMERS = List.of(
            customer(GENERAL, "Cliente General", "9999999999"), customer(ANA_1, "Ana Pérez", "1234567890"),
            customer(ANA_2, "Ana Pérez", "9876543210"), customer(LUIS, "Luis Gómez", "5555555555"));

    private HarnessFixture() {}

    static void stub(ProductFacade products, ServiceFacade services, CustomerFacade customers, SettingsFacade settings) {
        when(products.searchProducts(anyString(), isNull(), eq(true), any())).thenAnswer(call -> page(
                PRODUCTS.stream().filter(p -> matches(p.name(), call.getArgument(0))).toList()));
        when(services.searchServices(anyString(), isNull(), eq(true), any())).thenAnswer(call -> page(
                SERVICES.stream().filter(s -> matches(s.name(), call.getArgument(0))).toList()));
        when(customers.searchCustomers(anyString(), any())).thenAnswer(call -> page(
                CUSTOMERS.stream().filter(c -> matches(c.fullName(), call.getArgument(0))).toList()));
        PRODUCTS.forEach(p -> when(products.getById(p.id())).thenReturn(Optional.of(p)));
        SERVICES.forEach(s -> when(services.getById(s.id())).thenReturn(Optional.of(s)));
        CUSTOMERS.forEach(c -> when(customers.getById(c.id())).thenReturn(Optional.of(c)));
        when(customers.getByIdentification("9999999999")).thenReturn(Optional.of(CUSTOMERS.getFirst()));
        when(settings.getActiveValuesByTopic("PAYMENT_METHODS")).thenReturn(List.of(
                method(1L, "CASH", "Efectivo"), method(2L, "NEQUI", "Nequi"), method(3L, "CARD", "Tarjeta")));
    }

    private static <T> Page<T> page(List<T> items) {
        return new PageImpl<>(items);
    }

    private static boolean matches(String name, String query) {
        return normalize(name).contains(normalize(query));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).trim();
    }

    private static ProductDto product(long id, String name, String price, int stock) {
        return new ProductDto(id, "P-" + id, null, name, null, null, null,
                BigDecimal.ONE, new BigDecimal(price), stock, 0, true, null);
    }

    private static CustomerDto customer(long id, String name, String identification) {
        return new CustomerDto(id, name, identification, "private@example.com", "3101234567", "Private address", true);
    }

    private static ParameterValueDto method(Long id, String code, String label) {
        return new ParameterValueDto(id, "PAYMENT_METHODS", code, label, null, 0, true);
    }
}
