package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingConnectionTestResult;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.TransitionBillingStatusRequest;
import com.sciencebot.pos.billing.internal.controllers.StoreBillingConfigController;
import com.sciencebot.pos.billing.internal.services.StoreBillingConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoreBillingConfigControllerTest {

    private static final Long STORE = 5L;

    @Mock
    private StoreBillingConfigService service;

    @Mock
    private Authentication authentication;

    private StoreBillingConfigController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(authentication.getName()).thenReturn("super@platform.co");
        controller = new StoreBillingConfigController(service);
    }

    private static BillingActivationDto dto(BillingActivationStatus status) {
        return new BillingActivationDto(STORE, status, null, BillingEnvironment.SANDBOX, false, false, null, null, false, false, null);
    }

    @Test
    void listByStatus_DelegatesToService() {
        when(service.listByStatus(BillingActivationStatus.IN_SETUP))
                .thenReturn(List.of(dto(BillingActivationStatus.IN_SETUP)));

        ResponseEntity<List<BillingActivationDto>> response =
                controller.listByStatus(BillingActivationStatus.IN_SETUP);

        assertEquals(1, response.getBody().size());
        verify(service).listByStatus(BillingActivationStatus.IN_SETUP);
    }

    @Test
    void get_DelegatesToService() {
        when(service.get(STORE)).thenReturn(dto(BillingActivationStatus.ACTIVE));

        ResponseEntity<BillingActivationDto> response = controller.get(STORE);

        assertEquals(BillingActivationStatus.ACTIVE, response.getBody().status());
        verify(service).get(STORE);
    }

    @Test
    void testConnection_DelegatesToService() {
        BillingConnectionTestResult result = BillingConnectionTestResult.success(BillingEnvironment.SANDBOX, "Factus");
        when(service.testConnection(STORE, BillingEnvironment.SANDBOX)).thenReturn(result);

        ResponseEntity<BillingConnectionTestResult> response = controller.testConnection(STORE, BillingEnvironment.SANDBOX);

        assertSame(result, response.getBody());
    }

    @Test
    void switchEnvironment_PassesTheAuthenticatedUserAsActor() {
        when(service.switchEnvironment(STORE, BillingEnvironment.PRODUCTION, "super@platform.co"))
                .thenReturn(dto(BillingActivationStatus.ACTIVE));

        controller.switchEnvironment(STORE, BillingEnvironment.PRODUCTION, authentication);

        verify(service).switchEnvironment(STORE, BillingEnvironment.PRODUCTION, "super@platform.co");
    }

    @Test
    void transition_PassesStatusReasonAndActor() {
        TransitionBillingStatusRequest request = new TransitionBillingStatusRequest(BillingActivationStatus.SUSPENDED, "RUT ilegible");
        when(service.transition(STORE, BillingActivationStatus.SUSPENDED, "RUT ilegible", "super@platform.co"))
                .thenReturn(dto(BillingActivationStatus.SUSPENDED));

        ResponseEntity<BillingActivationDto> response = controller.transition(STORE, request, authentication);

        assertEquals(BillingActivationStatus.SUSPENDED, response.getBody().status());
        verify(service).transition(STORE, BillingActivationStatus.SUSPENDED, "RUT ilegible", "super@platform.co");
    }
}
