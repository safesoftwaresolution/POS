package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.services.ServiceDto;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.settings.ParameterValueDto;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.voicesales.VoiceTicketDto;
import com.sciencebot.pos.voicesales.internal.resolution.VoiceSaleResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketToolsTest {
    private final ProductFacade products = mock(ProductFacade.class);
    private final ServiceFacade services = mock(ServiceFacade.class);
    private final CustomerFacade customers = mock(CustomerFacade.class);
    private final SettingsFacade settings = mock(SettingsFacade.class);
    private TicketTools tools;

    @BeforeEach
    void setUp() {
        TenantContext.setStoreId(7L);
        when(products.searchProducts(anyString(), isNull(), eq(true), any())).thenReturn(Page.empty());
        when(services.searchServices(anyString(), isNull(), eq(true), any())).thenReturn(Page.empty());
        when(customers.searchCustomers(anyString(), any())).thenReturn(Page.empty());
        when(customers.getByIdentification("9999999999")).thenReturn(Optional.of(customer(1L, "Cliente General", "9999999999")));
        when(settings.getActiveValuesByTopic("PAYMENT_METHODS")).thenReturn(List.of(
                method(1L, "CASH", "Efectivo"), method(2L, "NEQUI", "Nequi")));
        when(products.searchProducts(eq("Café grande"), isNull(), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(product(11L, "Café grande", "3500", 40))));
        when(products.getById(11L)).thenReturn(Optional.of(product(11L, "Café grande", "3500", 40)));
        when(products.searchProducts(eq("Agua"), isNull(), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(product(12L, "Agua", "2000", 2))));
        when(products.getById(12L)).thenReturn(Optional.of(product(12L, "Agua", "2000", 2)));
        when(services.searchServices(eq("Corte"), isNull(), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(service(31L, "Corte", "15000"))));
        when(services.getById(31L)).thenReturn(Optional.of(service(31L, "Corte", "15000")));
        when(customers.getById(23L)).thenReturn(Optional.of(customer(23L, "Luis Gómez", "5555555555")));
        tools = new TicketTools(new TicketDraft(), new VoiceSaleResolver(products, services, customers, settings),
                products, services, customers, settings);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void addingItemsTakesPriceFromTheCatalogAndDefaultsToGeneralCustomer() {
        tools.agregarArticulo("Café grande", 2);
        tools.agregarArticulo("Corte", 1);

        VoiceTicketDto ticket = tools.ticket();

        assertThat(ticket.lines()).extracting(VoiceTicketDto.Line::name).containsExactly("Café grande", "Corte");
        assertThat(ticket.lines().getFirst().unitPrice()).isEqualByComparingTo("3500");
        assertThat(ticket.lines().getFirst().quantity()).isEqualTo(2);
        assertThat(ticket.subtotal()).isEqualByComparingTo("22000");
        assertThat(ticket.customer().name()).isEqualTo("Cliente General");
        assertThat(ticket.customer().id()).isEqualTo(1L);
        assertThat(ticket.confirmationRequested()).isFalse();
    }

    @Test
    void addingTheSameItemTwiceAddsUpTheQuantity() {
        tools.agregarArticulo("Café grande", 1);
        tools.agregarArticulo("Café grande", 2);

        assertThat(tools.ticket().lines()).hasSize(1);
        assertThat(tools.ticket().lines().getFirst().quantity()).isEqualTo(3);
    }

    @Test
    void unknownOrAmbiguousItemsAreNotAddedAndTheModelGetsTheOptions() {
        when(products.searchProducts(eq("Gaseosa"), isNull(), eq(true), any())).thenReturn(new PageImpl<>(List.of(
                product(21L, "Gaseosa cola", "3000", 9), product(22L, "Gaseosa limón", "3000", 9))));

        String unknown = tools.agregarArticulo("Pizza", 1);
        String ambiguous = tools.agregarArticulo("Gaseosa", 1);

        assertThat(tools.ticket().lines()).isEmpty();
        assertThat(unknown).contains("No encontré");
        assertThat(ambiguous).contains("Gaseosa cola").contains("Gaseosa limón");
    }

    @Test
    void stockAndQuantityLimitsAreEnforcedWithoutChangingTheTicket() {
        String overStock = tools.agregarArticulo("Agua", 5);
        String invalid = tools.agregarArticulo("Café grande", 0);
        String tooMany = tools.agregarArticulo("Café grande", 1001);

        assertThat(tools.ticket().lines()).isEmpty();
        assertThat(overStock).contains("2");
        assertThat(invalid).contains("cantidad");
        assertThat(tooMany).contains("cantidad");
    }

    @Test
    void removeAndChangeQuantityWorkOnTicketLines() {
        tools.agregarArticulo("Café grande", 2);
        tools.agregarArticulo("Corte", 1);

        tools.cambiarCantidad("café", 4);
        tools.quitarArticulo("corte");

        assertThat(tools.ticket().lines()).hasSize(1);
        assertThat(tools.ticket().lines().getFirst().quantity()).isEqualTo(4);
        assertThat(tools.quitarArticulo("pizza")).contains("No hay");
    }

    @Test
    void customerPaymentAndCashAreResolvedAgainstTheStoreNeverInvented() {
        when(customers.searchCustomers(eq("Ana Pérez"), any())).thenReturn(new PageImpl<>(List.of(
                customer(21L, "Ana Pérez", "1234567890"), customer(22L, "Ana Pérez", "9876543210"))));
        when(customers.searchCustomers(eq("Luis Gómez"), any())).thenReturn(new PageImpl<>(List.of(
                customer(23L, "Luis Gómez", "5555555555"))));

        String ambiguous = tools.asignarCliente("Ana Pérez");
        assertThat(tools.ticket().customer().name()).isEqualTo("Cliente General");
        assertThat(ambiguous).contains("Ana Pérez").doesNotContain("1234567890", "9876543210");

        tools.asignarCliente("Luis Gómez");
        assertThat(tools.ticket().customer().id()).isEqualTo(23L);
        tools.usarClienteGeneral();
        assertThat(tools.ticket().customer().id()).isEqualTo(1L);

        tools.fijarMedioPago("nequi");
        tools.fijarEfectivoRecibido(new BigDecimal("50000"));
        assertThat(tools.ticket().paymentMethod().code()).isEqualTo("NEQUI");
        assertThat(tools.ticket().cashReceived()).isEqualByComparingTo("50000");
        assertThat(tools.fijarMedioPago("bitcoin")).contains("No encontré");
        assertThat(tools.fijarEfectivoRecibido(new BigDecimal("-1"))).contains("monto");
        assertThat(tools.ticket().paymentMethod().code()).isEqualTo("NEQUI");
    }

    @Test
    void confirmationNeedsAtLeastOneLineAndNeverRegistersAnything() {
        assertThat(tools.pedirConfirmacion()).contains("vacío");
        assertThat(tools.ticket().confirmationRequested()).isFalse();

        tools.agregarArticulo("Café grande", 1);
        tools.pedirConfirmacion();

        assertThat(tools.ticket().confirmationRequested()).isTrue();
        verify(products, never()).adjustStock(anyLong(), anyInt());
        verify(products, never()).updateStock(anyLong(), anyInt());
    }

    @Test
    void anyChangeAfterAskingForConfirmationWithdrawsIt() {
        tools.agregarArticulo("Café grande", 1);
        tools.pedirConfirmacion();
        tools.cambiarCantidad("café", 2);

        assertThat(tools.ticket().confirmationRequested()).isFalse();
    }

    @Test
    void clearingEmptiesTheTicket() {
        tools.agregarArticulo("Café grande", 1);
        tools.fijarMedioPago("nequi");

        tools.vaciarTicket();

        assertThat(tools.ticket().lines()).isEmpty();
        assertThat(tools.ticket().paymentMethod()).isNull();
    }

    private static ProductDto product(Long id, String name, String price, int stock) {
        return new ProductDto(id, "P-" + id, null, name, null, null, null,
                BigDecimal.ONE, new BigDecimal(price), stock, 0, true, null);
    }

    private static ServiceDto service(Long id, String name, String price) {
        return new ServiceDto(id, "S-" + id, name, null, null, null, new BigDecimal(price),
                BigDecimal.ZERO, null, "94", null, true);
    }

    private static CustomerDto customer(Long id, String name, String identification) {
        return new CustomerDto(id, name, identification, "private@example.com", "3101234567", "Private address", true);
    }

    private static ParameterValueDto method(Long id, String code, String label) {
        return new ParameterValueDto(id, "PAYMENT_METHODS", code, label, null, 0, true);
    }
}
