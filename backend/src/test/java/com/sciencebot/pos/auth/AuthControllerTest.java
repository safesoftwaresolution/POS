package com.sciencebot.pos.auth;

import com.sciencebot.pos.auth.internal.controllers.AuthController;
import com.sciencebot.pos.auth.internal.services.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.List;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authController.setCookieProperties("jwt_token", false, "Strict");
    }

    @Test
    void login_ReturnsTokenAndSetsHttpOnlyCookie() {
        LoginResponse mockResponse = new LoginResponse(
                "mocked-jwt-token",
                "mocked-refresh-token",
                "admin",
                "ADMINISTRATOR",
                1L,
                900L
        );
        when(authService.login("admin", "admin123")).thenReturn(mockResponse);

        ResponseEntity<LoginResponse> response = authController.login(
                new LoginRequest("admin", "admin123")
        );

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("admin", response.getBody().username());
        assertEquals("mocked-jwt-token", response.getBody().token());
        assertEquals("mocked-refresh-token", response.getBody().refreshToken());
        assertEquals(1L, response.getBody().storeId());

        List<String> setCookieHeaders = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookieHeaders);

        String accessCookie = setCookieHeaders.stream()
                .filter(c -> c.startsWith("jwt_token=")).findFirst().orElse(null);
        assertNotNull(accessCookie);
        assertTrue(accessCookie.contains("jwt_token=mocked-jwt-token"));
        assertTrue(accessCookie.contains("HttpOnly"));
        assertTrue(accessCookie.contains("SameSite=Strict"));
        assertTrue(accessCookie.contains("Max-Age=900"));
        assertTrue(accessCookie.contains("Path=/"));

        String refreshCookie = setCookieHeaders.stream()
                .filter(c -> c.startsWith("refresh_token=")).findFirst().orElse(null);
        assertNotNull(refreshCookie);
        assertTrue(refreshCookie.contains("refresh_token=mocked-refresh-token"));
        assertTrue(refreshCookie.contains("HttpOnly"));
        assertTrue(refreshCookie.contains("Path=/api/v1/auth"));
    }

    @Test
    void login_MissingFields_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new LoginRequest("admin", null));
        assertTrue(ex.getMessage().contains("requeridos"));
    }
}