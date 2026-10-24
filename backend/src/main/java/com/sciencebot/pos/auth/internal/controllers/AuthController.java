package com.sciencebot.pos.auth.internal.controllers;

import com.sciencebot.pos.auth.internal.services.AuthService;
import com.sciencebot.pos.users.RegisterOwnerCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.sciencebot.pos.auth.LoginRequest;
import com.sciencebot.pos.auth.LoginResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticacion", description = "Login y gestion de sesion JWT con Cookies HttpOnly y soporte Bearer Token")
public class AuthController {

    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;

    @Value("${security.jwt.cookie-name:jwt_token}")
    private String cookieName = "jwt_token";

    @Value("${security.jwt.refresh-cookie-name:refresh_token}")
    private String refreshCookieName = "refresh_token";

    @Value("${security.jwt.cookie-secure:false}")
    private boolean cookieSecure = false;

    @Value("${security.jwt.cookie-same-site:Strict}")
    private String cookieSameSite = "Strict";

    @Value("${security.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs = 604800000L;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public void setCookieProperties(String cookieName, boolean cookieSecure, String cookieSameSite) {
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
            summary = "Iniciar sesion",
            description = "Autentica al usuario con credenciales username y password."
    )
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        if (loginRequest == null) {
        throw new IllegalArgumentException("Los campos username y password son requeridos");
    }
        LoginResponse response = authService.login(loginRequest.username(), loginRequest.password());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie(response.token(), response.expiresIn()).toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(response.refreshToken(), refreshExpirationMs / 1000).toString())
                .body(response);
    }

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(
            summary = "Auto-registro publico",
            description = "Crea una cuenta ADMINISTRATOR pendiente de verificar su correo y envia el enlace de verificacion."
    )
    public ResponseEntity<Void> register(@RequestBody RegisterOwnerCommand command) {
        authService.register(command);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/verify-email")
    @SecurityRequirements
    @Operation(
            summary = "Verificar correo",
            description = "Consume el token del enlace de verificacion, activa el correo del usuario y lo autologuea."
    )
    public ResponseEntity<LoginResponse> verifyEmail(@RequestParam String token) {
        LoginResponse response = authService.verifyEmail(token);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie(response.token(), response.expiresIn()).toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(response.refreshToken(), refreshExpirationMs / 1000).toString())
                .body(response);
    }

    @PostMapping("/resend-verification")
    @SecurityRequirements
    @Operation(
            summary = "Reenviar verificacion de correo",
            description = "Reenvia el enlace de verificacion si la cuenta existe y sigue sin verificar. "
                    + "Responde 204 siempre, exista o no el correo."
    )
    public ResponseEntity<Void> resendVerification(@RequestBody Map<String, String> body) {
        authService.resendVerification(body == null ? null : body.get("email"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @SecurityRequirements
    @Operation(
            summary = "Solicitar recuperacion de contrasena",
            description = "Envia un enlace de un solo uso (vigencia 1 hora) si la cuenta existe y esta verificada. "
                    + "Responde 204 siempre, exista o no el correo."
    )
    public ResponseEntity<Void> forgotPassword(@RequestBody Map<String, String> body) {
        authService.forgotPassword(body == null ? null : body.get("email"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @SecurityRequirements
    @Operation(
            summary = "Restablecer contrasena",
            description = "Consume el token del enlace de recuperacion, fija la nueva contrasena y cierra todas las sesiones del usuario."
    )
    public ResponseEntity<Void> resetPassword(@RequestBody Map<String, String> body) {
        authService.resetPassword(
                body == null ? null : body.get("token"),
                body == null ? null : body.get("newPassword"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(
            summary = "Renovar sesion",
            description = "Emite un nuevo access token usando el refresh token (cookie HttpOnly o campo refreshToken en el body). Rota el refresh token."
    )
    public ResponseEntity<LoginResponse> refresh(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request
    ) {
        String rawRefreshToken = extractRefreshToken(body, request);
        LoginResponse response = authService.refresh(rawRefreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie(response.token(), response.expiresIn()).toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(response.refreshToken(), refreshExpirationMs / 1000).toString())
                .body(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesion")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request
    ) {
        authService.logout(extractRefreshToken(body, request));

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie(cookieName, "/").toString())
                .header(HttpHeaders.SET_COOKIE, expiredCookie(refreshCookieName, REFRESH_COOKIE_PATH).toString())
                .build();
    }

    private String extractRefreshToken(Map<String, String> body, HttpServletRequest request) {
        if (body != null && body.get("refreshToken") != null && !body.get("refreshToken").isBlank()) {
            return body.get("refreshToken");
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (refreshCookieName.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private ResponseCookie accessCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private ResponseCookie refreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(refreshCookieName, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }

    private ResponseCookie expiredCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(path)
                .maxAge(0)
                .build();
    }
}