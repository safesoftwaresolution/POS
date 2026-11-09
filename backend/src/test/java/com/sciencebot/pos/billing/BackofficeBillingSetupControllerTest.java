package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.BillingSetupDto;
import com.sciencebot.pos.billing.internal.controllers.BackofficeBillingSetupController;
import com.sciencebot.pos.billing.internal.services.BillingSetupService;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** El back office sigue el asistente de cualquier local, solo lectura y con el local de la ruta. */
class BackofficeBillingSetupControllerTest {

    private static final Long STORE = 7L;

    private BillingSetupService service;
    private BackofficeBillingSetupController controller;

    @BeforeEach
    void setUp() {
        service = mock(BillingSetupService.class);
        controller = new BackofficeBillingSetupController(service);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void returnsTheSetupOfTheStoreInThePath() {
        BillingSetupDto setup = new BillingSetupDto(BillingActivationStatus.IN_SETUP, BillingEnvironment.PRODUCTION,
                true, List.of(), null, 0);
        when(service.getSetup(STORE)).thenReturn(setup);

        assertSame(setup, controller.getStoreSetup(STORE).getBody());
        verify(service).getSetup(STORE);
    }

    @Test
    void usesThePathStoreEvenIfAnotherStoreIsInContext() {
        TenantContext.setStoreId(99L);
        when(service.getSetup(STORE)).thenReturn(new BillingSetupDto(BillingActivationStatus.ACTIVE,
                BillingEnvironment.PRODUCTION, true, List.of(), null, 1));

        controller.getStoreSetup(STORE);

        verify(service).getSetup(STORE);
        verify(service, never()).getSetup(99L);
    }

    @Test
    void onlyTheSuperAdminCanReadIt() throws NoSuchMethodException {
        var method = BackofficeBillingSetupController.class.getMethod("getStoreSetup", Long.class);

        assertEquals("/api/v1/backoffice/stores/{storeId}/billing/setup",
                method.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("hasRole('SUPER_ADMIN')", method.getAnnotation(PreAuthorize.class).value());
    }
}
