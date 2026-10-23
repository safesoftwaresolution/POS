package com.sciencebot.pos.auth.internal.services;

import com.sciencebot.pos.auth.LoginResponse;
import com.sciencebot.pos.config.security.JwtService;
import com.sciencebot.pos.config.security.PosUserDetails;
import com.sciencebot.pos.shared.email.EmailSender;
import com.sciencebot.pos.users.RegisterOwnerCommand;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserFacade userFacade;
    private final EmailVerificationService emailVerificationService;
    private final EmailSender emailSender;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final long RESEND_COOLDOWN_MS = 60_000L;

    private final ConcurrentHashMap<String, LockoutDetails> lockoutCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastResendAt = new ConcurrentHashMap<>();

    public AuthService(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokenService, UserFacade userFacade,
                       EmailVerificationService emailVerificationService, EmailSender emailSender) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userFacade = userFacade;
        this.emailVerificationService = emailVerificationService;
        this.emailSender = emailSender;
    }

    /** Auto-registro publico: crea al futuro dueno pendiente de verificar su correo y le envia el enlace. */
    public void register(RegisterOwnerCommand command) {
        UserDto created = userFacade.registerPendingAdmin(command);
        try {
            sendVerificationEmail(created);
        } catch (RuntimeException e) {
            // La cuenta ya existe: si el correo falla no se le devuelve un error al usuario (reintentar
            // chocaria con usuario/correo duplicado); puede pedir el reenvio desde el login.
            log.warn("Cuenta {} creada pero no se pudo enviar el correo de verificacion", created.username(), e);
        }
    }

    /**
     * Reenvia el enlace de verificacion. Responde siempre igual (exista o no la cuenta) para no revelar
     * que correos estan registrados, y limita el reenvio a uno por correo cada {@value #RESEND_COOLDOWN_MS} ms.
     */
    public void resendVerification(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String cleanEmail = email.trim().toLowerCase();

        long now = System.currentTimeMillis();
        Long last = lastResendAt.get(cleanEmail);
        if (last != null && now - last < RESEND_COOLDOWN_MS) {
            return;
        }

        userFacade.findByEmail(cleanEmail)
                .filter(u -> !u.emailVerified())
                .ifPresent(u -> {
                    lastResendAt.put(cleanEmail, now);
                    try {
                        sendVerificationEmail(u);
                    } catch (RuntimeException e) {
                        log.warn("No se pudo reenviar la verificacion a la cuenta {}", u.username(), e);
                    }
                });
    }

    private void sendVerificationEmail(UserDto user) {
        String rawToken = emailVerificationService.issue(user.id());
        String link = frontendUrl + "/verify-email?token=" + rawToken;
        emailSender.send(
                user.email(),
                "Verifica tu correo - ProPOS",
                "<p>Hola " + user.fullName() + ",</p>"
                        + "<p>Gracias por registrarte en ProPOS. Confirma tu correo para activar tu cuenta:</p>"
                        + "<p><a href=\"" + link + "\">Verificar mi correo</a></p>"
                        + "<p>Si no fuiste tu, ignora este mensaje.</p>"
        );
    }

    /** Consume el token de verificacion, activa el correo del usuario y lo autologuea. */
    public LoginResponse verifyEmail(String rawToken) {
        Long userId = emailVerificationService.consume(rawToken);
        UserDto user = userFacade.markEmailVerified(userId);

        String token = jwtService.generateToken(user.username(), user.role(), user.storeId());
        String refreshToken = refreshTokenService.issue(user.id());

        return new LoginResponse(
                token,
                refreshToken,
                user.username(),
                user.role(),
                user.storeId(),
                jwtService.getExpirationTime()
        );
    }

    public LoginResponse login(String username, String password) {
        if (username == null || password == null) {
            throw new BadCredentialsException("Usuario o contrasena incorrectos");
        }

        String cleanUsername = username.trim().toLowerCase();

        checkLockout(cleanUsername);

        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(cleanUsername);
        } catch (Exception e) {
            recordFailedAttempt(cleanUsername);
            throw new BadCredentialsException("Usuario o contrasena incorrectos");
        }

        if (!userDetails.isEnabled()) {
            boolean unverified = userFacade.findByUsername(cleanUsername)
                    .map(u -> !u.emailVerified())
                    .orElse(false);
            if (unverified) {
                throw new DisabledException("Debes verificar tu correo electronico antes de iniciar sesion.");
            }
            throw new DisabledException("Cuenta de usuario desactivada");
        }

        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            recordFailedAttempt(cleanUsername);
            throw new BadCredentialsException("Usuario o contrasena incorrectos");
        }

        lockoutCache.remove(cleanUsername);

        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("SELLER");

        Long storeId = null;
        if (userDetails instanceof PosUserDetails posUserDetails) {
            storeId = posUserDetails.getStoreId();
        }

        Long userId = userFacade.findByUsername(userDetails.getUsername())
                .map(UserDto::id)
                .orElseThrow(() -> new BadCredentialsException("Usuario o contrasena incorrectos"));

        String token = jwtService.generateToken(userDetails, role, storeId);
        String refreshToken = refreshTokenService.issue(userId);

        return new LoginResponse(
                token,
                refreshToken,
                userDetails.getUsername(),
                role,
                storeId,
                jwtService.getExpirationTime()
        );
    }

    /**
     * Renueva el access token rotando el refresh token (single-use). Si se detecta
     * reuso de un token ya rotado, {@code RefreshTokenService} revoca la sesion completa.
     */
    public LoginResponse refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(rawRefreshToken);

        UserDto user;
        try {
            user = userFacade.getById(rotation.userId());
        } catch (Exception e) {
            refreshTokenService.revokeFamily(rotation.familyId());
            throw new BadCredentialsException("Refresh token invalido");
        }

        if (!user.active()) {
            refreshTokenService.revokeFamily(rotation.familyId());
            throw new DisabledException("Cuenta de usuario desactivada");
        }

        String token = jwtService.generateToken(user.username(), user.role(), user.storeId());

        return new LoginResponse(
                token,
                rotation.newRawToken(),
                user.username(),
                user.role(),
                user.storeId(),
                jwtService.getExpirationTime()
        );
    }

    /** Cierra la sesion revocando el refresh token asociado (si se envia). */
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private void checkLockout(String username) {
        LockoutDetails details = lockoutCache.get(username);
        if (details != null && details.isLocked()) {
            throw new LockedException("Cuenta bloqueada temporalmente por intentos fallidos. Intente de nuevo mas tarde.");
        }
    }

    private void recordFailedAttempt(String username) {
        lockoutCache.compute(username, (key, details) -> {
            long now = System.currentTimeMillis();
            if (details == null || now - details.firstAttemptTime > 15 * 60 * 1000) {
                return new LockoutDetails(1, now, 0);
            }

            int newCount = details.count + 1;
            long lockedUntil = 0;
            if (newCount >= 5) {
                lockedUntil = now + 30 * 60 * 1000;
            }
            return new LockoutDetails(newCount, details.firstAttemptTime, lockedUntil);
        });
    }

    private static class LockoutDetails {
        final int count;
        final long firstAttemptTime;
        final long lockedUntil;

        LockoutDetails(int count, long firstAttemptTime, long lockedUntil) {
            this.count = count;
            this.firstAttemptTime = firstAttemptTime;
            this.lockedUntil = lockedUntil;
        }

        boolean isLocked() {
            return lockedUntil > System.currentTimeMillis();
        }
    }
}