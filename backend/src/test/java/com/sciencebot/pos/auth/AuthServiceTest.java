package com.sciencebot.pos.auth;

import com.sciencebot.pos.auth.internal.services.AuthService;
import com.sciencebot.pos.auth.internal.services.EmailVerificationService;
import com.sciencebot.pos.auth.internal.services.RefreshTokenService;
import com.sciencebot.pos.config.security.JwtService;
import com.sciencebot.pos.config.security.PosUserDetails;
import com.sciencebot.pos.shared.email.EmailSender;
import com.sciencebot.pos.users.RegisterOwnerCommand;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock private UserDetailsService userDetailsService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private UserFacade userFacade;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private EmailSender emailSender;
    @InjectMocks private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static UserDto userDto(boolean active) {
        return new UserDto(7L, "Admin", "admin", "admin@pos.com", "ADMINISTRATOR", active, 1L, true);
    }

    private static UserDto unverifiedUserDto() {
        return new UserDto(7L, "Admin", "admin", "admin@pos.com", "ADMINISTRATOR", true, null, false);
    }

    @Test
    void login_Success() {
        PosUserDetails userDetails = new PosUserDetails(
                "admin", "encodedPassword", true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR")),
                1L
        );

        when(userDetailsService.loadUserByUsername("admin")).thenReturn(userDetails);
        when(passwordEncoder.matches("Password123", "encodedPassword")).thenReturn(true);
        when(userFacade.findByUsername("admin")).thenReturn(Optional.of(userDto(true)));
        when(jwtService.generateToken(any(org.springframework.security.core.userdetails.UserDetails.class), eq("ADMINISTRATOR"), eq(1L))).thenReturn("testToken");
        when(jwtService.getExpirationTime()).thenReturn(900L);
        when(refreshTokenService.issue(7L)).thenReturn("testRefreshToken");

        LoginResponse response = authService.login("admin", "Password123");

        assertNotNull(response);
        assertEquals("testToken", response.token());
        assertEquals("testRefreshToken", response.refreshToken());
        assertEquals("admin", response.username());
        assertEquals("ADMINISTRATOR", response.role());
        assertEquals(1L, response.storeId());
        verify(refreshTokenService).issue(7L);
    }

    @Test
    void refresh_RotatesTokenAndIssuesNewAccessToken() {
        var rotation = new RefreshTokenService.RotationResult("newRefresh", 7L, "fam-1");

        when(refreshTokenService.rotate("oldRefresh")).thenReturn(rotation);
        when(userFacade.getById(7L)).thenReturn(userDto(true));
        when(jwtService.generateToken("admin", "ADMINISTRATOR", 1L)).thenReturn("newAccess");
        when(jwtService.getExpirationTime()).thenReturn(900L);

        LoginResponse response = authService.refresh("oldRefresh");

        assertEquals("newAccess", response.token());
        assertEquals("newRefresh", response.refreshToken());
        assertEquals("admin", response.username());
        assertEquals(1L, response.storeId());
        verify(refreshTokenService).rotate("oldRefresh");
    }

    @Test
    void refresh_InactiveUser_RevokesFamilyAndThrows() {
        var rotation = new RefreshTokenService.RotationResult("newRefresh", 7L, "fam-1");

        when(refreshTokenService.rotate("oldRefresh")).thenReturn(rotation);
        when(userFacade.getById(7L)).thenReturn(userDto(false));

        assertThrows(DisabledException.class, () -> authService.refresh("oldRefresh"));
        verify(refreshTokenService).revokeFamily("fam-1");
    }

    @Test
    void logout_RevokesRefreshToken() {
        authService.logout("someRefresh");
        verify(refreshTokenService).revoke("someRefresh");
    }

    @Test
    void login_Failure_LocksOutAfter5Attempts() {
        when(userDetailsService.loadUserByUsername("admin")).thenThrow(new UsernameNotFoundException(""));

        for (int i = 0; i < 4; i++) {
            assertThrows(BadCredentialsException.class, () -> authService.login("admin", "wrong"));
        }

        assertThrows(BadCredentialsException.class, () -> authService.login("admin", "wrong"));

        LockedException ex = assertThrows(LockedException.class, () -> authService.login("admin", "wrong"));
        assertTrue(ex.getMessage().contains("bloqueada"));
    }

    @Test
    void login_UnverifiedEmail_ThrowsSpecificMessage() {
        PosUserDetails userDetails = new PosUserDetails(
                "admin", "encodedPassword", false, // enabled=false porque emailVerified=false
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR")),
                null
        );
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(userDetails);
        when(userFacade.findByUsername("admin")).thenReturn(Optional.of(unverifiedUserDto()));

        DisabledException ex = assertThrows(DisabledException.class, () -> authService.login("admin", "Password123"));
        assertTrue(ex.getMessage().contains("verificar tu correo"));
    }

    @Test
    void register_Success_CreatesUserAndSendsVerificationEmail() {
        RegisterOwnerCommand command = new RegisterOwnerCommand("Carlos Martinez", "carlos.m", "carlos@empresa.com", "Password123");
        UserDto created = new UserDto(3L, "Carlos Martinez", "carlos.m", "carlos@empresa.com", "ADMINISTRATOR", true, null, false);

        when(userFacade.registerPendingAdmin(command)).thenReturn(created);
        when(emailVerificationService.issue(3L)).thenReturn("raw-token");

        authService.register(command);

        verify(userFacade).registerPendingAdmin(command);
        verify(emailVerificationService).issue(3L);
        verify(emailSender).send(eq("carlos@empresa.com"), anyString(), contains("raw-token"));
    }

    @Test
    void register_EmailFailure_StillCreatesAccountWithoutThrowing() {
        RegisterOwnerCommand command = new RegisterOwnerCommand("Carlos Martinez", "carlos.m", "carlos@empresa.com", "Password123");
        UserDto created = new UserDto(3L, "Carlos Martinez", "carlos.m", "carlos@empresa.com", "ADMINISTRATOR", true, null, false);

        when(userFacade.registerPendingAdmin(command)).thenReturn(created);
        when(emailVerificationService.issue(3L)).thenReturn("raw-token");
        doThrow(new IllegalStateException("smtp down")).when(emailSender).send(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> authService.register(command));
        verify(userFacade).registerPendingAdmin(command);
    }

    @Test
    void resendVerification_UnverifiedAccount_SendsNewLinkOnce() {
        UserDto pending = new UserDto(3L, "Carlos", "carlos.m", "carlos@empresa.com", "ADMINISTRATOR", true, null, false);
        when(userFacade.findByEmail("carlos@empresa.com")).thenReturn(Optional.of(pending));
        when(emailVerificationService.issue(3L)).thenReturn("new-token");

        authService.resendVerification("  Carlos@Empresa.com ");
        authService.resendVerification("carlos@empresa.com"); // dentro del enfriamiento: se ignora

        verify(emailSender, times(1)).send(eq("carlos@empresa.com"), anyString(), contains("new-token"));
    }

    @Test
    void resendVerification_VerifiedOrUnknownAccount_SendsNothing() {
        when(userFacade.findByEmail("ok@empresa.com")).thenReturn(Optional.of(userDto(true)));
        when(userFacade.findByEmail("nadie@empresa.com")).thenReturn(Optional.empty());

        authService.resendVerification("ok@empresa.com");
        authService.resendVerification("nadie@empresa.com");
        authService.resendVerification(null);

        verifyNoInteractions(emailSender);
    }

    @Test
    void verifyEmail_Success_MarksVerifiedAndAutoLogsIn() {
        UserDto verified = new UserDto(3L, "Carlos Martinez", "carlos.m", "carlos@empresa.com", "ADMINISTRATOR", true, null, true);

        when(emailVerificationService.consume("raw-token")).thenReturn(3L);
        when(userFacade.markEmailVerified(3L)).thenReturn(verified);
        when(jwtService.generateToken("carlos.m", "ADMINISTRATOR", null)).thenReturn("newAccess");
        when(jwtService.getExpirationTime()).thenReturn(900L);
        when(refreshTokenService.issue(3L)).thenReturn("newRefresh");

        LoginResponse response = authService.verifyEmail("raw-token");

        assertEquals("newAccess", response.token());
        assertEquals("newRefresh", response.refreshToken());
        assertEquals("carlos.m", response.username());
        assertNull(response.storeId());
    }
}