package com.sciencebot.pos.auth.internal.services;

import com.sciencebot.pos.shared.email.EmailSender;
import com.sciencebot.pos.users.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Genera y envia el enlace de recuperacion de contrasena fuera del hilo de la peticion. Asi la respuesta de
 * {@code /forgot-password} tarda lo mismo exista o no el correo (no se filtra por tiempo que cuentas hay) y un
 * SMTP lento no bloquea al usuario. Nunca deja escapar una excepcion: solo la loguea.
 */
@Component
public class PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);

    private final PasswordResetService passwordResetService;
    private final EmailSender emailSender;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public PasswordResetMailer(PasswordResetService passwordResetService, EmailSender emailSender) {
        this.passwordResetService = passwordResetService;
        this.emailSender = emailSender;
    }

    @Async
    public void sendResetLink(UserDto user) {
        try {
            String rawToken = passwordResetService.issue(user.id());
            String link = frontendUrl + "/reset-password?token=" + rawToken;
            emailSender.send(
                    user.email(),
                    "Restablece tu contraseña - ProPOS",
                    "<p>Hola " + user.fullName() + ",</p>"
                            + "<p>Recibimos una solicitud para restablecer tu contraseña de ProPOS. "
                            + "El enlace vence en 1 hora:</p>"
                            + "<p><a href=\"" + link + "\">Restablecer mi contraseña</a></p>"
                            + "<p>Si no fuiste tu, ignora este mensaje: tu contraseña no cambiará.</p>"
            );
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar el correo de recuperacion a la cuenta {}", user.username(), e);
        }
    }
}
