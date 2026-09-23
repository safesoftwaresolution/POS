package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.api.*;
import com.sciencebot.pos.billing.dto.*;
import com.sciencebot.pos.billing.exception.*;
import com.sciencebot.pos.billing.internal.controllers.BillingActivationController;
import com.sciencebot.pos.billing.internal.services.StoreBillingConfigService;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BillingActivationControllerTest {

    private static final Long STORE = 5L;

    @Mock
    private StoreBillingConfigService service;

    @Mock
    private Authentication authentication;

    private BillingActivationController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new BillingActivationController(service);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static BillingActivationDto dto(BillingActivationStatus status) {
        return new BillingActivationDto(STORE, status, null, BillingEnvironment.SANDBOX, false, false, null, null, null);
    }

    @Test
    void getStatus_UsesTheStoreOfTheCurrentRequest() {
        TenantContext.setStoreId(STORE);
        when(service.get(STORE)).thenReturn(dto(BillingActivationStatus.NOT_REQUESTED));

        ResponseEntity<BillingActivationDto> response = controller.getStatus();

        assertEquals(BillingActivationStatus.NOT_REQUESTED, response.getBody().status());
    }

    @Test
    void getStatus_WithoutTenantContextFails() {
        assertThrows(IllegalStateException.class, () -> controller.getStatus());
    }

    @Test
    void requestActivation_PassesTheAuthenticatedUserAsActor() {
        TenantContext.setStoreId(STORE);
        when(authentication.getName()).thenReturn("admin@local.co");
        when(service.requestActivation(STORE, "admin@local.co"))
                .thenReturn(dto(BillingActivationStatus.DOCUMENTS_SUBMITTED));

        ResponseEntity<BillingActivationDto> response = controller.requestActivation(authentication);

        assertEquals(BillingActivationStatus.DOCUMENTS_SUBMITTED, response.getBody().status());
        verify(service).requestActivation(STORE, "admin@local.co");
    }

    @Test
    void requestActivation_NeverReceivesCredentials() {
        // El controlador no declara ningún parámetro de credenciales: lo garantiza el compilador,
        // pero lo dejamos explícito como intención del diseño (defensa en profundidad).
        TenantContext.setStoreId(STORE);
        when(authentication.getName()).thenReturn("admin@local.co");
        when(service.requestActivation(any(), any())).thenReturn(dto(BillingActivationStatus.DOCUMENTS_SUBMITTED));

        controller.requestActivation(authentication);

        verify(service, never()).updateCredentials(any(), any(), any(), any());
    }
}
