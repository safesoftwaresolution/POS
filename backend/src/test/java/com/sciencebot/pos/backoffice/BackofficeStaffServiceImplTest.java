package com.sciencebot.pos.backoffice;

import com.sciencebot.pos.auth.AuthFacade;
import com.sciencebot.pos.backoffice.internal.services.BackofficeStaffServiceImpl;
import com.sciencebot.pos.users.CreateUserCommand;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BackofficeStaffServiceImplTest {

    @Mock private UserFacade userFacade;
    @Mock private AuthFacade authFacade;
    @InjectMocks private BackofficeStaffServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static UserDto operator(long id, boolean emailVerified) {
        return new UserDto(id, "Ana Torres", "ana", "ana@platform.com", "SUPER_ADMIN", true, null, emailVerified);
    }

    @Test
    void createStaff_createsPendingInvitationAndSendsEmail() {
        when(userFacade.createUnverifiedUser(any(CreateUserCommand.class))).thenReturn(operator(5L, false));

        BackofficeStaffDto dto = service.createStaff(
                new CreateBackofficeStaffCommand("Ana Torres", "ana", "ana@platform.com", "Password123"));

        assertFalse(dto.emailVerified());
        verify(userFacade, never()).createUser(any());
        verify(authFacade).resendVerificationToUser(5L);
    }

    @Test
    void createStaff_whenEmailFails_propagatesSoTheCreationRollsBack() {
        when(userFacade.createUnverifiedUser(any(CreateUserCommand.class))).thenReturn(operator(5L, false));
        doThrow(new IllegalStateException("smtp down")).when(authFacade).resendVerificationToUser(5L);

        assertThrows(IllegalStateException.class, () -> service.createStaff(
                new CreateBackofficeStaffCommand("Ana Torres", "ana", "ana@platform.com", "Password123")));
    }

    @Test
    void resendInvitation_resendsToAPendingOperator() {
        when(userFacade.getById(5L)).thenReturn(operator(5L, false));

        service.resendInvitation(5L);

        verify(authFacade).resendVerificationToUser(5L);
    }

    @Test
    void resendInvitation_rejectsAccountsThatAreNotOperators() {
        when(userFacade.getById(9L)).thenReturn(
                new UserDto(9L, "Luis", "luis", "luis@tienda.com", "SELLER", true, 1L, false));

        assertThrows(EntityNotFoundException.class, () -> service.resendInvitation(9L));
        verify(authFacade, never()).resendVerificationToUser(anyLong());
    }
}
