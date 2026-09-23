package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.api.*;
import com.sciencebot.pos.billing.dto.*;
import com.sciencebot.pos.billing.exception.*;
import com.sciencebot.pos.billing.internal.adapters.NumberingRangeProvider;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusCredentialsResolver;
import com.sciencebot.pos.billing.internal.adapters.mock.MockBillingAdapter;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.repositories.NumberingRangeAuditRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.billing.internal.services.NumberingRangeServiceImpl;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NumberingRangeServiceImplTest {

    private static final Long STORE = 42L;

    @Mock
    private NumberingRangeProvider customProvider;

    @Mock
    private StoreBillingConfigRepository billingConfigRepository;

    @Mock
    private NumberingRangeAuditRepository auditRepository;

    private MockBillingAdapter mockAdapter;
    private NumberingRangeServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockAdapter = new MockBillingAdapter();

        when(customProvider.getProviderName()).thenReturn("factus");
        when(billingConfigRepository.findById(any())).thenReturn(Optional.empty());

        service = new NumberingRangeServiceImpl(List.of(mockAdapter, customProvider), billingConfigRepository, auditRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void useCustomProvider() {
        ReflectionTestUtils.setField(service, "activeProviderName", "factus");
    }

    private static StoreBillingConfig configWithActiveRange(int rangeId) {
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(STORE);
        config.setEnvironment(BillingEnvironment.SANDBOX);
        config.getSandbox().setNumberingRangeId(rangeId);
        return config;
    }

    @Test
    void numberingRanges_MockProvider_ReturnsRanges() {
        ReflectionTestUtils.setField(service, "activeProviderName", "mock");

        List<NumberingRangeDto> dianRanges = service.queryDianNumberingRanges();
        assertNotNull(dianRanges);
        assertFalse(dianRanges.isEmpty());
        assertEquals("SETP", dianRanges.get(0).prefix());

        List<NumberingRangeDto> factusRanges = service.listNumberingRanges();
        assertNotNull(factusRanges);
        assertFalse(factusRanges.isEmpty());

        NumberingRangeDto singleRange = service.getNumberingRange(1L);
        assertNotNull(singleRange);
        assertEquals(1L, singleRange.id());

        CreateNumberingRangeRequest createReq = new CreateNumberingRangeRequest(
                "01", "SETP", "18764000001234", 1L
        );
        NumberingRangeDto created = service.createNumberingRange(createReq);
        assertNotNull(created);
        assertEquals(100L, created.id());

        service.deleteNumberingRange(1L);
        assertNotNull(service.toggleNumberingRangeStatus(1L));
        assertEquals(150L, service.updateNumberingRangeCurrent(1L, 150L).current());
    }

    // --- El consecutivo no puede retroceder (aplica siempre, con o sin local en contexto) ---

    @Test
    void updateCurrent_CannotGoBackwardsRelativeToWhatTheProviderReports() {
        useCustomProvider();
        when(customProvider.getNumberingRange(14L)).thenReturn(
                new NumberingRangeDto(14L, "01", "SETP", "1876", 1L, 5000L, 100L, null, null, null, true));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateNumberingRangeCurrent(14L, 50L));

        assertTrue(ex.getMessage().contains("retroceder"));
        verify(customProvider, never()).updateNumberingRangeCurrent(any(), any());
    }

    @Test
    void updateCurrent_AdvancingOrStayingTheSameIsAllowed() {
        useCustomProvider();
        when(customProvider.getNumberingRange(14L)).thenReturn(
                new NumberingRangeDto(14L, "01", "SETP", "1876", 1L, 5000L, 100L, null, null, null, true));
        when(customProvider.updateNumberingRangeCurrent(14L, 100L)).thenReturn(
                new NumberingRangeDto(14L, "01", "SETP", "1876", 1L, 5000L, 100L, null, null, null, true));

        assertDoesNotThrow(() -> service.updateNumberingRangeCurrent(14L, 100L));
    }

    // --- No se puede tocar el rango configurado como activo del local ---

    @Test
    void delete_TheConfiguredRangeIsBlocked() {
        TenantContext.setStoreId(STORE);
        when(billingConfigRepository.findById(STORE)).thenReturn(Optional.of(configWithActiveRange(14)));
        useCustomProvider();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.deleteNumberingRange(14L));

        assertTrue(ex.getMessage().contains("eliminar"));
        verify(customProvider, never()).deleteNumberingRange(any());
    }

    @Test
    void delete_ADifferentRangeThanTheConfiguredOneIsAllowed() {
        TenantContext.setStoreId(STORE);
        when(billingConfigRepository.findById(STORE)).thenReturn(Optional.of(configWithActiveRange(14)));
        useCustomProvider();

        assertDoesNotThrow(() -> service.deleteNumberingRange(99L));
        verify(customProvider).deleteNumberingRange(99L);
    }

    @Test
    void toggle_DeactivatingTheConfiguredRangeIsBlocked() {
        TenantContext.setStoreId(STORE);
        when(billingConfigRepository.findById(STORE)).thenReturn(Optional.of(configWithActiveRange(14)));
        useCustomProvider();
        when(customProvider.getNumberingRange(14L)).thenReturn(
                new NumberingRangeDto(14L, "01", "SETP", "1876", 1L, 5000L, 1L, null, null, null, true));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.toggleNumberingRangeStatus(14L));

        assertTrue(ex.getMessage().contains("desactivar"));
        verify(customProvider, never()).toggleNumberingRangeStatus(any());
    }

    @Test
    void toggle_DeactivatingADifferentRangeThanTheConfiguredOneIsAllowed() {
        TenantContext.setStoreId(STORE);
        when(billingConfigRepository.findById(STORE)).thenReturn(Optional.of(configWithActiveRange(14)));
        useCustomProvider();
        when(customProvider.getNumberingRange(99L)).thenReturn(
                new NumberingRangeDto(99L, "01", "SETP", "1876", 1L, 5000L, 1L, null, null, null, true));
        when(customProvider.toggleNumberingRangeStatus(99L)).thenReturn(
                new NumberingRangeDto(99L, "01", "SETP", "1876", 1L, 5000L, 1L, null, null, null, false));

        assertDoesNotThrow(() -> service.toggleNumberingRangeStatus(99L));
    }

    // --- Como máximo un rango activo por tipo de documento ---

    @Test
    void toggle_ActivatingWhenAnotherRangeOfTheSameDocumentIsAlreadyActiveIsBlocked() {
        TenantContext.setStoreId(STORE);
        useCustomProvider();
        when(customProvider.getNumberingRange(20L)).thenReturn(
                new NumberingRangeDto(20L, "01", "OTRO", "999", 1L, 5000L, 1L, null, null, null, false));
        when(customProvider.listNumberingRanges()).thenReturn(List.of(
                new NumberingRangeDto(14L, "01", "SETP", "1876", 1L, 5000L, 1L, null, null, null, true)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.toggleNumberingRangeStatus(20L));

        assertTrue(ex.getMessage().contains("01"));
        verify(customProvider, never()).toggleNumberingRangeStatus(any());
    }

    @Test
    void toggle_ActivatingWhenNoOtherActiveRangeOfTheSameDocumentIsAllowed() {
        TenantContext.setStoreId(STORE);
        useCustomProvider();
        when(customProvider.getNumberingRange(20L)).thenReturn(
                new NumberingRangeDto(20L, "01", "OTRO", "999", 1L, 5000L, 1L, null, null, null, false));
        when(customProvider.listNumberingRanges()).thenReturn(List.of());
        when(customProvider.toggleNumberingRangeStatus(20L)).thenReturn(
                new NumberingRangeDto(20L, "01", "OTRO", "999", 1L, 5000L, 1L, null, null, null, true));

        assertDoesNotThrow(() -> service.toggleNumberingRangeStatus(20L));
    }

    @Test
    void create_WithoutStoreInContextSkipsTheDuplicateDocumentGuard() {
        // Sin TenantContext (p. ej. un test que llama al servicio directo) no se puede
        // atribuir la regla a un local: se deja pasar en vez de fallar en falso.
        useCustomProvider();
        when(customProvider.listNumberingRanges()).thenReturn(List.of(
                new NumberingRangeDto(14L, "01", "SETP", "1876", 1L, 5000L, 1L, null, null, null, true)));
        CreateNumberingRangeRequest request = new CreateNumberingRangeRequest("01", "NEW", "111", 1L);
        when(customProvider.createNumberingRange(request)).thenReturn(
                new NumberingRangeDto(21L, "01", "NEW", "111", 1L, 5000L, 1L, null, null, null, true));

        assertDoesNotThrow(() -> service.createNumberingRange(request));
    }

    // --- Auditoría ---

    @Test
    void changesAreAuditedWithTheAuthenticatedUser() {
        TenantContext.setStoreId(STORE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@local.co", null, List.of()));
        useCustomProvider();
        when(customProvider.getNumberingRange(14L)).thenReturn(
                new NumberingRangeDto(14L, "01", "SETP", "1876", 1L, 5000L, 100L, null, null, null, true));
        when(customProvider.updateNumberingRangeCurrent(14L, 200L)).thenReturn(
                new NumberingRangeDto(14L, "01", "SETP", "1876", 1L, 5000L, 200L, null, null, null, true));

        service.updateNumberingRangeCurrent(14L, 200L);

        verify(auditRepository).save(argThat(entry ->
                entry.getStoreId().equals(STORE)
                        && entry.getRangeId().equals(14L)
                        && entry.getAction().equals("UPDATE_CURRENT")
                        && entry.getPreviousValue().equals("100")
                        && entry.getNewValue().equals("200")
                        && entry.getPerformedBy().equals("admin@local.co")));
    }

    // --- Rango activo elegido por el local ---

    private static NumberingRangeDto range(long id, boolean active) {
        return new NumberingRangeDto(id, "01", "SETP", "1876", 1L, 5000L, 1L, null, null, null, active);
    }

    private static StoreBillingConfig activeConfigWithCredentials() {
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(STORE);
        config.setStatus(BillingActivationStatus.ACTIVE);
        config.setEnvironment(BillingEnvironment.SANDBOX);
        config.getSandbox().setClientId("client");
        config.getSandbox().setClientSecret("secret");
        config.getSandbox().setUsername("user");
        config.getSandbox().setPassword("pass");
        return config;
    }

    @Test
    void setDefault_StoresTheRangeForTheActiveEnvironmentAndAuditsIt() {
        TenantContext.setStoreId(STORE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@local.co", null, List.of()));
        StoreBillingConfig config = configWithActiveRange(14);
        config.setEnvironment(BillingEnvironment.PRODUCTION);
        config.getProduction().setNumberingRangeId(7);
        when(billingConfigRepository.findById(STORE)).thenReturn(Optional.of(config));
        useCustomProvider();
        when(customProvider.getNumberingRange(20L)).thenReturn(range(20L, true));

        NumberingRangeDto result = service.setDefaultNumberingRange(20L);

        assertEquals(20L, result.id());
        assertEquals(20, config.getProduction().getNumberingRangeId());
        assertEquals(14, config.getSandbox().getNumberingRangeId(), "el otro entorno no se toca");
        verify(billingConfigRepository).save(config);
        verify(auditRepository).save(argThat(entry ->
                entry.getStoreId().equals(STORE)
                        && entry.getRangeId().equals(20L)
                        && entry.getAction().equals("SET_DEFAULT")
                        && entry.getPreviousValue().equals("7")
                        && entry.getNewValue().equals("PRODUCTION:20")
                        && entry.getPerformedBy().equals("admin@local.co")));
    }

    @Test
    void setDefault_RangeThatDoesNotExistInTheProviderIsRejected() {
        TenantContext.setStoreId(STORE);
        StoreBillingConfig config = configWithActiveRange(14);
        when(billingConfigRepository.findById(STORE)).thenReturn(Optional.of(config));
        useCustomProvider();
        when(customProvider.getNumberingRange(99L)).thenReturn(null);

        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.setDefaultNumberingRange(99L));

        assertEquals(14, config.getSandbox().getNumberingRangeId());
        verify(billingConfigRepository, never()).save(any());
        verifyNoInteractions(auditRepository);
    }

    @Test
    void setDefault_InactiveRangeIsRejected() {
        TenantContext.setStoreId(STORE);
        StoreBillingConfig config = configWithActiveRange(14);
        when(billingConfigRepository.findById(STORE)).thenReturn(Optional.of(config));
        useCustomProvider();
        when(customProvider.getNumberingRange(20L)).thenReturn(range(20L, false));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.setDefaultNumberingRange(20L));

        assertTrue(ex.getMessage().contains("inactivo"));
        assertEquals(14, config.getSandbox().getNumberingRangeId());
        verify(billingConfigRepository, never()).save(any());
        verifyNoInteractions(auditRepository);
    }

    @Test
    void setDefault_StoreWithoutBillingConfigIsRejected() {
        TenantContext.setStoreId(STORE);
        useCustomProvider();

        assertThrows(BillingNotEnabledException.class, () -> service.setDefaultNumberingRange(20L));
        verify(customProvider, never()).getNumberingRange(any());
    }

    @Test
    void setDefault_WithoutStoreInContextIsRejected() {
        useCustomProvider();

        assertThrows(BillingNotEnabledException.class, () -> service.setDefaultNumberingRange(20L));
    }

    @Test
    void setDefault_NextInvoiceResolvesTheNewlyAssignedRange() {
        // El flujo de venta obtiene el rango a través de FactusCredentialsResolver, que lee la misma
        // configuración que acaba de actualizar el administrador.
        TenantContext.setStoreId(STORE);
        StoreBillingConfig config = activeConfigWithCredentials();
        config.getSandbox().setNumberingRangeId(14);
        when(billingConfigRepository.findById(STORE)).thenReturn(Optional.of(config));
        useCustomProvider();
        when(customProvider.getNumberingRange(20L)).thenReturn(range(20L, true));
        FactusCredentialsResolver resolver =
                new FactusCredentialsResolver(billingConfigRepository, "https://sandbox", "https://prod");

        assertEquals(14, resolver.resolve(STORE).numberingRangeId());
        service.setDefaultNumberingRange(20L);
        assertEquals(20, resolver.resolve(STORE).numberingRangeId());
    }

    @Test
    void create_FirstRangeOfAStoreWithoutOneBecomesTheDefault() {
        TenantContext.setStoreId(STORE);
        StoreBillingConfig config = activeConfigWithCredentials();
        when(billingConfigRepository.findById(STORE)).thenReturn(Optional.of(config));
        useCustomProvider();
        when(customProvider.listNumberingRanges()).thenReturn(List.of());
        CreateNumberingRangeRequest request = new CreateNumberingRangeRequest("01", "NEW", "111", 1L);
        when(customProvider.createNumberingRange(request)).thenReturn(range(21L, true));

        service.createNumberingRange(request);

        assertEquals(21, config.getSandbox().getNumberingRangeId());
        verify(billingConfigRepository).save(config);
        verify(auditRepository).save(argThat(entry -> entry.getAction().equals("CREATE")));
        verify(auditRepository).save(argThat(entry -> entry.getAction().equals("SET_DEFAULT")));
    }

    @Test
    void create_DoesNotReplaceARangeTheStoreAlreadyConfigured() {
        TenantContext.setStoreId(STORE);
        StoreBillingConfig config = configWithActiveRange(14);
        when(billingConfigRepository.findById(STORE)).thenReturn(Optional.of(config));
        useCustomProvider();
        when(customProvider.listNumberingRanges()).thenReturn(List.of());
        CreateNumberingRangeRequest request = new CreateNumberingRangeRequest("01", "NEW", "111", 1L);
        when(customProvider.createNumberingRange(request)).thenReturn(range(21L, true));

        service.createNumberingRange(request);

        assertEquals(14, config.getSandbox().getNumberingRangeId());
        verify(billingConfigRepository, never()).save(any());
        verify(auditRepository, never()).save(argThat(entry -> entry.getAction().equals("SET_DEFAULT")));
    }

    @Test
    void changesWithoutAStoreInContextAreNotAudited() {
        useCustomProvider();
        when(customProvider.getNumberingRange(14L)).thenReturn(
                new NumberingRangeDto(14L, "01", "SETP", "1876", 1L, 5000L, 1L, null, null, null, true));
        when(customProvider.updateNumberingRangeCurrent(14L, 5L)).thenReturn(
                new NumberingRangeDto(14L, "01", "SETP", "1876", 1L, 5000L, 5L, null, null, null, true));

        service.updateNumberingRangeCurrent(14L, 5L);

        verifyNoInteractions(auditRepository);
    }
}
