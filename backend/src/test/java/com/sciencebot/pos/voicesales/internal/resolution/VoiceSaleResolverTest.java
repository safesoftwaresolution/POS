package com.sciencebot.pos.voicesales.internal.resolution;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.services.ServiceDto;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.settings.ParameterValueDto;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.voicesales.VoiceSalePreviewDto;
import com.sciencebot.pos.voicesales.internal.ports.SaleIntent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Kind.*;
import static com.sciencebot.pos.voicesales.VoiceSalePreviewDto.Status.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoiceSaleResolverTest {
    private final ProductFacade products = mock(ProductFacade.class);
    private final ServiceFacade services = mock(ServiceFacade.class);
    private final CustomerFacade customers = mock(CustomerFacade.class);
    private final SettingsFacade settings = mock(SettingsFacade.class);
    private VoiceSaleResolver resolver;

    @BeforeEach
    void setUp() {
        TenantContext.setStoreId(7L);
        resolver = new VoiceSaleResolver(products, services, customers, settings);
        when(products.searchProducts(anyString(), isNull(), eq(true), any())).thenReturn(Page.empty());
        when(services.searchServices(anyString(), isNull(), eq(true), any())).thenReturn(Page.empty());
        when(customers.searchCustomers(anyString(), any())).thenReturn(Page.empty());
        when(settings.getActiveValuesByTopic("PAYMENT_METHODS")).thenReturn(List.of(
                method(1L, "CASH", "Efectivo", true), method(2L, "CARD", "Tarjeta", true)));
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void exampleKeepsHomonymsAndLiteralPluralSuggestionsWithoutCreatingASale() {
        when(products.searchProducts(eq("cafés grandes"), isNull(), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(product(11L, "Café grande", true))));
        when(products.searchProducts(eq("botella de agua"), isNull(), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(product(12L, "Botella de agua", true))));
        when(customers.searchCustomers(eq("Ana Pérez"), any())).thenReturn(new PageImpl<>(List.of(
                customer(21L, "Ana Pérez", "1234567890", true),
                customer(22L, "Ana Pérez", "9876543210", true))));

        var draft = resolver.resolve(new SaleIntent(List.of(new SaleIntent.Item("cafés grandes", 2),
                new SaleIntent.Item("botella de agua", 1)), "Ana Pérez", "efectivo", new BigDecimal("50000")));

        assertThat(draft.items()).hasSize(2);
        assertThat(draft.items().getFirst().quantity()).isEqualTo(2);
        assertThat(draft.items().getFirst().item().status()).isEqualTo(NEEDS_REVIEW);
        assertThat(draft.items().get(1).item().status()).isEqualTo(RESOLVED);
        assertThat(draft.customer().status()).isEqualTo(AMBIGUOUS);
        assertThat(draft.customer().candidates()).extracting(VoiceSalePreviewDto.Candidate::id).containsExactly(21L, 22L);
        assertThat(draft.customer().candidates()).extracting(VoiceSalePreviewDto.Candidate::label).doesNotHaveDuplicates();
        assertThat(draft.customer().candidates().toString()).doesNotContain("1234567890", "9876543210", "private@example.com");
        assertThat(draft.paymentMethod().status()).isEqualTo(RESOLVED);
        assertThat(draft.paymentMethod().candidates().getFirst().code()).isEqualTo("CASH");
        assertThat(draft.cashReceived()).isEqualByComparingTo("50000");
        assertThat(draft.pendingFields()).containsExactly("items[0].item", "customer");
        verify(products, atLeastOnce()).searchProducts(anyString(), isNull(), eq(true), any());
        verify(services, atLeastOnce()).searchServices(anyString(), isNull(), eq(true), any());
        verify(customers).searchCustomers(eq("Ana Pérez"), any());
        verify(settings).getActiveValuesByTopic("PAYMENT_METHODS");
        verifyNoMoreInteractions(products, services, customers, settings);
        verify(products, never()).adjustStock(anyLong(), anyInt());
        verify(products, never()).updateStock(anyLong(), anyInt());
        verify(products, never()).createProduct(any());
        verify(services, never()).createService(any());
        verify(customers, never()).createCustomer(any());
    }

    @Test
    void uniqueNormalizedExactMatchCanResolveButMissingQuantityAndCashRemainMissing() {
        when(products.searchProducts(anyString(), isNull(), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(product(11L, "Café Grande", true))));
        var draft = resolver.resolve(intent("  CAFÉ   GRANDE ", null, "CASH", null));
        assertThat(draft.items().getFirst().item().status()).isEqualTo(RESOLVED);
        assertThat(draft.items().getFirst().item().candidates().getFirst().kind()).isEqualTo(PRODUCT);
        assertThat(draft.items().getFirst().quantity()).isNull();
        assertThat(draft.cashReceived()).isNull();
        assertThat(draft.pendingFields()).contains("items[0].quantity", "customer", "cashReceived");
    }

    @Test
    void productServiceNameCollisionDoesNotPreferOneKind() {
        when(products.searchProducts(anyString(), isNull(), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(product(11L, "Lavado", true))));
        when(services.searchServices(anyString(), isNull(), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(service(11L, "Lavado", true))));
        var reference = resolver.resolve(intent("lavado", 1, null, null)).items().getFirst().item();
        assertThat(reference.status()).isEqualTo(AMBIGUOUS);
        assertThat(reference.candidates()).extracting(VoiceSalePreviewDto.Candidate::kind).containsExactly(PRODUCT, SERVICE);
    }

    @Test
    void nonexistentArticleAndUndictatedCustomerAndPaymentAreNotInvented() {
        var draft = resolver.resolve(intent("inexistente", 1, null, null));
        assertThat(draft.items().getFirst().item().status()).isEqualTo(NOT_FOUND);
        assertThat(draft.customer().status()).isEqualTo(NOT_PROVIDED);
        assertThat(draft.paymentMethod().status()).isEqualTo(NOT_PROVIDED);
        assertThat(draft.pendingFields()).containsExactly("items[0].item", "customer", "paymentMethod");
        verifyNoInteractions(customers);
    }

    @Test
    void singlePartialHitIsOnlyASuggestionAndInactiveRecordsAreExcluded() {
        when(products.searchProducts(anyString(), isNull(), eq(true), any())).thenReturn(new PageImpl<>(List.of(
                product(1L, "Café", false), product(2L, "Café grande", true))));
        var reference = resolver.resolve(intent("café", 1, "CARD", null)).items().getFirst().item();
        assertThat(reference.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(reference.candidates()).extracting(VoiceSalePreviewDto.Candidate::id).containsExactly(2L);
        assertThat(resolver.resolve(intent("café", 1, "CARD", null)).pendingFields()).doesNotContain("cashReceived");
    }

    @Test
    void truncatedPagesCannotProveUniqueEvenWithOneExactResult() {
        when(products.searchProducts(anyString(), isNull(), eq(true), any())).thenReturn(
                new PageImpl<>(List.of(product(1L, "Café", true)), PageRequest.of(0, 20), 21));
        assertThat(resolver.resolve(intent("café", 1, null, null)).items().getFirst().item().status())
                .isEqualTo(NEEDS_REVIEW);
    }

    @Test
    void customerPartialAndTruncatedMatchesRequireReviewAndHideDocuments() {
        when(customers.searchCustomers(eq("Ana"), any())).thenReturn(new PageImpl<>(List.of(
                customer(1L, "Ana Pérez", "1234567890", true), customer(2L, "Ana", "5555555555", false))));
        var draft = resolver.resolve(new SaleIntent(List.of(), "Ana", "CARD", null));
        assertThat(draft.customer().status()).isEqualTo(NEEDS_REVIEW);
        assertThat(draft.customer().candidates()).extracting(VoiceSalePreviewDto.Candidate::id).containsExactly(1L);
        when(customers.searchCustomers(eq("Ana"), any())).thenReturn(new PageImpl<>(List.of(
                customer(1L, "Ana", "1234567890", true)), PageRequest.of(0, 20), 21));
        assertThat(resolver.resolve(new SaleIntent(List.of(), "Ana", "CARD", null)).customer().status())
                .isEqualTo(NEEDS_REVIEW);
    }

    @Test
    void customerHomonymsRemainDistinguishableEvenWhenTheirMaskedDocumentsMatch() {
        when(customers.searchCustomers(eq("Ana Pérez"), any())).thenReturn(new PageImpl<>(List.of(
                customer(21L, "Ana Pérez", "1234567890", true),
                customer(22L, "Ana Pérez", "9876567890", true))));
        var reference = resolver.resolve(new SaleIntent(List.of(), "Ana Pérez", null, null)).customer();
        assertThat(reference.status()).isEqualTo(AMBIGUOUS);
        assertThat(reference.candidates()).extracting(VoiceSalePreviewDto.Candidate::label).doesNotHaveDuplicates();
        assertThat(reference.candidates()).extracting(VoiceSalePreviewDto.Candidate::code).containsOnlyNulls();
    }

    @Test
    void wildcardAndEmptyMentionsNeverTriggerBroadCatalogSearches() {
        clearInvocations(products, services, customers, settings);
        var draft = resolver.resolve(new SaleIntent(List.of(new SaleIntent.Item("%", null),
                new SaleIntent.Item("   ", null), new SaleIntent.Item("_", 1)), "%", "_", null));
        assertThat(draft.items()).allSatisfy(line -> assertThat(line.item().status()).isNotEqualTo(RESOLVED));
        verifyNoInteractions(products, services, customers, settings);
    }

    @Test
    void pluralLookupCanOfferSingularCatalogEntryWithoutResolvingIt() {
        when(products.searchProducts(eq("café"), isNull(), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(product(1L, "Café grande", true))));
        var reference = resolver.resolve(intent("cafés grandes", 2, null, null)).items().getFirst().item();
        assertThat(reference.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(reference.candidates()).extracting(VoiceSalePreviewDto.Candidate::id).containsExactly(1L);
    }

    @Test
    void paymentMatchesActiveLocalCodesOrLabelsOnlyWithoutCashFallback() {
        when(settings.getActiveValuesByTopic("PAYMENT_METHODS")).thenReturn(List.of(
                method(1L, "CASH", "Efectivo", false), method(2L, "NEQUI", "Nequi", true)));
        var absent = resolver.resolve(intent("agua", 1, "efectivo", null));
        assertThat(absent.paymentMethod().status()).isEqualTo(NOT_FOUND);
        assertThat(absent.pendingFields()).doesNotContain("cashReceived");
        assertThat(resolver.resolve(intent("agua", 1, "Neq", null)).paymentMethod().status()).isEqualTo(NEEDS_REVIEW);
        assertThat(resolver.resolve(intent("agua", 1, "nequi", null)).paymentMethod().status()).isEqualTo(RESOLVED);
    }

    @Test
    void zeroNegativeAndMissingFieldsRemainEditableAndPending() {
        var draft = resolver.resolve(intent(null, 0, "CASH", new BigDecimal("-1")));
        assertThat(draft.items().getFirst().quantity()).isZero();
        assertThat(draft.cashReceived()).isEqualByComparingTo("-1");
        assertThat(draft.pendingFields()).contains("items[0].item", "items[0].quantity", "cashReceived");
        assertThat(resolver.resolve(new SaleIntent(List.of(), null, null, null)).pendingFields()).contains("items");
    }

    @Test
    void refusesMissingTenantBeforeAnyFacadeCall() {
        TenantContext.clear();
        clearInvocations(products, services, customers, settings);
        assertThatThrownBy(() -> resolver.resolve(intent("café", 1, "CASH", null)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(products, services, customers, settings);
    }

    private static SaleIntent intent(String item, Integer quantity, String payment, BigDecimal cash) {
        return new SaleIntent(List.of(new SaleIntent.Item(item, quantity)), null, payment, cash);
    }

    private static ProductDto product(Long id, String name, boolean active) {
        return new ProductDto(id, "P-" + id, null, name, null, null, null,
                BigDecimal.ONE, BigDecimal.TEN, 100, 0, active, null);
    }

    private static ServiceDto service(Long id, String name, boolean active) {
        return new ServiceDto(id, "S-" + id, name, null, null, null, BigDecimal.TEN,
                BigDecimal.ZERO, null, "94", null, active);
    }

    private static CustomerDto customer(Long id, String name, String identification, boolean active) {
        return new CustomerDto(id, name, identification, "private@example.com", "3101234567", "Private address", active);
    }

    private static ParameterValueDto method(Long id, String code, String label, boolean active) {
        return new ParameterValueDto(id, "PAYMENT_METHODS", code, label, null, 0, active);
    }
}
