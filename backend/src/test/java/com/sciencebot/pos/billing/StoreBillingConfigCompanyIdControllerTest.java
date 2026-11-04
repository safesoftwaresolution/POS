package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.UpdateProviderCompanyIdCommand;
import com.sciencebot.pos.billing.internal.controllers.StoreBillingConfigController;
import com.sciencebot.pos.billing.internal.services.StoreBillingConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoreBillingConfigCompanyIdControllerTest {

    @Test
    void updateProviderCompanyId_PassesEnvironmentCompanyIdAndActorToTheService() {
        StoreBillingConfigService service = mock(StoreBillingConfigService.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("super@platform.co");
        BillingActivationDto dto = new BillingActivationDto(5L, BillingActivationStatus.NOT_REQUESTED,
                null, BillingEnvironment.SANDBOX, false, false, null, null, true, false, null);
        when(service.updateProviderCompanyId(5L, BillingEnvironment.SANDBOX, "abc", "super@platform.co")).thenReturn(dto);

        ResponseEntity<BillingActivationDto> response = new StoreBillingConfigController(service)
                .updateProviderCompanyId(5L, BillingEnvironment.SANDBOX, new UpdateProviderCompanyIdCommand("abc"), authentication);

        assertTrue(response.getBody().sandboxCompanyIdConfigured());
        verify(service).updateProviderCompanyId(5L, BillingEnvironment.SANDBOX, "abc", "super@platform.co");
    }

    @Test
    void updateProviderCompanyId_IsReservedToSuperAdmin() throws Exception {
        PreAuthorize rule = StoreBillingConfigController.class.getMethod("updateProviderCompanyId",
                Long.class, BillingEnvironment.class, UpdateProviderCompanyIdCommand.class, Authentication.class)
                .getAnnotation(PreAuthorize.class);

        assertNotNull(rule);
        assertEquals("hasRole('SUPER_ADMIN')", rule.value());
    }
}
