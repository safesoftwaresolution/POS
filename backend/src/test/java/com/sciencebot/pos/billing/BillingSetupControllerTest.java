package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.BillingSetupDto;
import com.sciencebot.pos.billing.dto.DianEnablementRequest;
import com.sciencebot.pos.billing.dto.FiscalProfileRequest;
import com.sciencebot.pos.billing.dto.NumberingImportResultDto;
import com.sciencebot.pos.billing.internal.controllers.BillingSetupController;
import com.sciencebot.pos.billing.internal.services.BillingSetupService;
import com.sciencebot.pos.config.tenant.TenantContext;
import jakarta.validation.Valid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BillingSetupControllerTest {

    private static final Long STORE = 5L;
    private static final String TEST_SET = "d7b8f2c4-1a2b-4c3d-9e8f-0a1b2c3d4e5f";

    private BillingSetupService service;
    private Authentication authentication;
    private BillingSetupController controller;

    @BeforeEach
    void setUp() {
        service = mock(BillingSetupService.class);
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin@local.co");
        controller = new BillingSetupController(service);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static BillingSetupDto setup() {
        return new BillingSetupDto(BillingActivationStatus.IN_SETUP, BillingEnvironment.PRODUCTION, true, List.of(),
                null, 0);
    }

    @Test
    void getSetup_UsesTheStoreOfTheCurrentRequest() {
        TenantContext.setStoreId(STORE);
        when(service.getSetup(STORE)).thenReturn(setup());

        assertEquals(BillingActivationStatus.IN_SETUP, controller.getSetup().getBody().status());
        verify(service).getSetup(STORE);
    }

    @Test
    void everyEndpointNeedsAStoreInContextAndNeverFallsBackToAnother() {
        FiscalProfileRequest profile = FiscalProfileRequestValidationTest.valid();

        assertThrows(IllegalStateException.class, () -> controller.getSetup());
        assertThrows(IllegalStateException.class, () -> controller.saveFiscalProfile(profile, authentication));
        assertThrows(IllegalStateException.class,
                () -> controller.startDianEnablement(new DianEnablementRequest(TEST_SET), authentication));
        assertThrows(IllegalStateException.class, () -> controller.importNumbering(authentication));
        verifyNoInteractions(service);
    }

    @Test
    void writesPassTheStoreInContextAndTheAuthenticatedUser() {
        TenantContext.setStoreId(STORE);
        FiscalProfileRequest profile = FiscalProfileRequestValidationTest.valid();
        when(service.saveFiscalProfile(any(), any(), any())).thenReturn(setup());
        when(service.startDianEnablement(any(), any(), any())).thenReturn(setup());
        when(service.importNumbering(any(), any())).thenReturn(new NumberingImportResultDto(1, 2, List.of(), setup()));

        controller.saveFiscalProfile(profile, authentication);
        controller.startDianEnablement(new DianEnablementRequest(TEST_SET), authentication);
        NumberingImportResultDto imported = controller.importNumbering(authentication).getBody();

        verify(service).saveFiscalProfile(STORE, profile, "admin@local.co");
        verify(service).startDianEnablement(STORE, TEST_SET, "admin@local.co");
        verify(service).importNumbering(STORE, "admin@local.co");
        assertEquals(1, imported.importedCount());
    }

    @Test
    void onlyTheAdministratorWritesAndTheSupervisorCanFollowTheProgress() throws NoSuchMethodException {
        assertEquals("/api/v1/billing/setup",
                BillingSetupController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertEquals("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')", preAuthorize("getSetup"));
        assertEquals("hasRole('ADMINISTRATOR')", preAuthorize("saveFiscalProfile",
                FiscalProfileRequest.class, Authentication.class));
        assertEquals("hasRole('ADMINISTRATOR')", preAuthorize("startDianEnablement",
                DianEnablementRequest.class, Authentication.class));
        assertEquals("hasRole('ADMINISTRATOR')", preAuthorize("importNumbering", Authentication.class));
    }

    @Test
    void requestBodiesAreValidated() throws NoSuchMethodException {
        assertTrue(firstParameterIsValid(BillingSetupController.class.getMethod("saveFiscalProfile",
                FiscalProfileRequest.class, Authentication.class)));
        assertTrue(firstParameterIsValid(BillingSetupController.class.getMethod("startDianEnablement",
                DianEnablementRequest.class, Authentication.class)));
    }

    private static String preAuthorize(String method, Class<?>... types) throws NoSuchMethodException {
        return BillingSetupController.class.getMethod(method, types).getAnnotation(PreAuthorize.class).value();
    }

    private static boolean firstParameterIsValid(Method method) {
        Parameter first = method.getParameters()[0];
        return Arrays.stream(first.getAnnotations()).anyMatch(a -> a.annotationType() == Valid.class);
    }
}
