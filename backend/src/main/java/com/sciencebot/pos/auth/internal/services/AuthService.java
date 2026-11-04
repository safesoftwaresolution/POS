package com.sciencebot.pos.auth.internal.services;

import com.sciencebot.pos.auth.AuthFacade;
import com.sciencebot.pos.auth.LoginResponse;
import com.sciencebot.pos.config.security.JwtService;
import com.sciencebot.pos.config.security.PosUserDetails;
import com.sciencebot.pos.shared.email.EmailSender;
import com.sciencebot.pos.shared.email.EmailTemplate;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService implements AuthFacade {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserFacade userFacade;
    private final EmailVerificationService emailVerificationService;
    private final EmailSender emailSender;
    private final PasswordResetService passwordResetService;
    private final PasswordResetMailer passwordResetMailer;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final long RESEND_COOLDOWN_MS = 60_000L;

    private final ConcurrentHashMap<String, LockoutDetails> lockoutCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastResendAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastForgotAt = new ConcurrentHashMap<>();

    public AuthService(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokenService, UserFacade userFacade,
                       EmailVerificationService emailVerificationService, EmailSender emailSender,
                       PasswordResetService passwordResetService, PasswordResetMailer passwordResetMailer) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userFacade = userFacade;
        this.emailVerificationService = emailVerificationService;
        this.emailSender = emailSender;
        this.passwordResetService = passwordResetService;
        this.passwordResetMailer = passwordResetMailer;
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

    /**
     * Inicia la recuperacion de contrasena: si la cuenta existe, esta activa y con el correo verificado,
     * envia un enlace de un solo uso. Responde siempre igual (no revela que correos existen) y limita a
     * un envio por correo cada {@value #RESEND_COOLDOWN_MS} ms. Una cuenta sin verificar debe verificar
     * primero su correo (ver {@link #resendVerification}).
     */
    public void forgotPassword(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String cleanEmail = email.trim().toLowerCase();

        long now = System.currentTimeMillis();
        Long last = lastForgotAt.get(cleanEmail);
        if (last != null && now - last < RESEND_COOLDOWN_MS) {
            return;
        }

        userFacade.findByEmail(cleanEmail)
                .filter(u -> u.active() && u.emailVerified())
                .ifPresent(u -> {
                    lastForgotAt.put(cleanEmail, now);
                    passwordResetMailer.sendResetLink(u); // asincrono: la respuesta no depende del SMTP
                });
    }

    /**
     * Inactiva o reactiva la cuenta de un usuario (acción del SUPER_ADMIN). Al inactivar cierra sus sesiones
     * abiertas. No aplica a otro SUPER_ADMIN; las demás reglas (no a sí mismo, ni al último administrador
     * activo del local, ni pasar el tope del plan al reactivar) las valida el módulo users.
     */
    @Override
    @Transactional
    public void setAccountActive(Long userId, boolean active) {
        UserDto user = userFacade.getById(userId);
        if ("SUPER_ADMIN".equals(user.role())) {
            throw new IllegalArgumentException("No se puede inactivar a un super administrador.");
        }
        userFacade.changeStatus(userId, active);
        if (!active) {
            refreshTokenService.revokeAllForUser(userId);
        }
    }

    /**
     * Elimina definitivamente la cuenta de un usuario ya verificado (acción del SUPER_ADMIN) junto con sus
     * tokens. Rechaza a un SUPER_ADMIN y a quien tenga historial (ventas, compras, movimientos o
     * devoluciones): a esos solo se les puede inactivar para no romper los registros del local.
     */
    @Override
    @Transactional
    public void deleteAccount(Long userId) {
        UserDto user = userFacade.getById(userId);
        if ("SUPER_ADMIN".equals(user.role())) {
            throw new IllegalArgumentException("No se puede eliminar a un super administrador.");
        }
        refreshTokenService.deleteAllForUser(userId);
        emailVerificationService.deleteForUser(userId);
        passwordResetService.deleteForUser(userId);
        userFacade.deleteUserPermanently(userId); // si tiene historial lanza 409 y todo lo anterior se revierte
        log.info("Cuenta {} (id {}) eliminada definitivamente por el super administrador", user.username(), userId);
    }

    /**
     * Consume el token y fija la nueva contrasena. Transaccional: si la contrasena no cumple los
     * requisitos, el token no queda consumido. Cierra todas las sesiones abiertas del usuario y le
     * levanta el bloqueo por intentos fallidos.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Long userId = passwordResetService.consume(rawToken);
        userFacade.resetPassword(userId, newPassword);
        refreshTokenService.revokeAllForUser(userId);
        lockoutCache.remove(userFacade.getById(userId).username().toLowerCase());
    }

    /** Tras un restablecimiento hecho por un administrador: la sesion vieja no debe sobrevivir a la contrasena vieja. */
    @Transactional
    public void closeSessionsAndClearLockout(Long userId, String username) {
        refreshTokenService.revokeAllForUser(userId);
        lockoutCache.remove(username.toLowerCase());
    }

    @Override
    @Transactional
    public void deleteUnverifiedAccount(Long userId) {
        refreshTokenService.deleteAllForUser(userId);
        emailVerificationService.deleteForUser(userId);
        passwordResetService.deleteForUser(userId);
        // Valida (ADMINISTRATOR sin verificar y sin local) y borra; si no cumple, la transaccion revierte lo anterior.
        userFacade.deleteUnverifiedOwner(userId);
    }

    @Override
    public void resendVerificationToUser(Long userId) {
        UserDto user = userFacade.getById(userId);
        if (user.emailVerified()) {
            throw new IllegalArgumentException("La cuenta ya tiene el correo verificado.");
        }
        String key = user.email().trim().toLowerCase();
        long now = System.currentTimeMillis();
        Long last = lastResendAt.get(key);
        if (last != null && now - last < RESEND_COOLDOWN_MS) {
            throw new IllegalArgumentException("Ya se envió un correo hace menos de un minuto. Espera antes de reintentar.");
        }
        sendVerificationEmail(user);
        lastResendAt.put(key, now);
    }

    private void sendVerificationEmail(UserDto user) {
        String rawToken = emailVerificationService.issue(user.id());
        String link = frontendUrl + "/verify-email?token=" + rawToken;
        String greeting = "<p style=\"margin:0 0 14px;\">Hola <strong>" + EmailTemplate.escape(user.fullName()) + "</strong>,</p>";
        if ("SUPER_ADMIN".equals(user.role())) {
            // Operador de la plataforma invitado desde el backoffice: al confirmar, la cuenta queda activa.
            emailSender.send(
                    user.email(),
                    "Te invitaron a PosBento como operador",
                    EmailTemplate.render(
                            frontendUrl,
                            "Acepta la invitación para activar tu cuenta de operador de PosBento.",
                            "Te invitaron como operador",
                            greeting
                                    + "<p style=\"margin:0;\">Te invitaron a administrar la plataforma PosBento como operador. "
                                    + "Tu usuario es <strong>" + EmailTemplate.escape(user.username()) + "</strong>. "
                                    + "Acepta la invitación para activar tu cuenta; el enlace vence en <strong>24 horas</strong>.</p>",
                            "Aceptar invitación",
                            link,
                            "Si no esperabas esta invitación, puedes ignorar este mensaje: la cuenta no se activará."));
            return;
        }
        emailSender.send(
                user.email(),
                "Verifica tu correo - PosBento",
                EmailTemplate.render(
                        frontendUrl,
                        "Confirma tu correo para activar tu cuenta de PosBento.",
                        "Confirma tu correo",
                        greeting
                                + "<p style=\"margin:0;\">Gracias por registrarte en PosBento. Confirma tu correo para activar tu cuenta "
                                + "y continuar con el registro de tu local.</p>",
                        "Verificar mi correo",
                        link,
                        "Si no creaste esta cuenta, puedes ignorar este mensaje."));
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
                jwtService.getExpirationTime(),
                user.fullName(),
                user.avatarUrl()
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

        UserDto account = userFacade.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Usuario o contrasena incorrectos"));
        Long userId = account.id();

        String token = jwtService.generateToken(userDetails, role, storeId);
        String refreshToken = refreshTokenService.issue(userId);

        return new LoginResponse(
                token,
                refreshToken,
                userDetails.getUsername(),
                role,
                storeId,
                jwtService.getExpirationTime(),
                account.fullName(),
                account.avatarUrl()
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
                jwtService.getExpirationTime(),
                user.fullName(),
                user.avatarUrl()
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