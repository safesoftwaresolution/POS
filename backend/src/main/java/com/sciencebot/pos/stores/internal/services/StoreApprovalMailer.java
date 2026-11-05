package com.sciencebot.pos.stores.internal.services;

import com.sciencebot.pos.shared.email.EmailSender;
import com.sciencebot.pos.shared.email.EmailTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Avisa por correo que un local fue aprobado y que ya se puede iniciar sesión. Corre fuera del hilo de la
 * petición y nunca deja escapar una excepción: un fallo de SMTP no debe deshacer ni demorar la aprobación.
 */
@Component
public class StoreApprovalMailer {

    private static final Logger log = LoggerFactory.getLogger(StoreApprovalMailer.class);

    private final EmailSender emailSender;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public StoreApprovalMailer(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async
    public void sendApproved(String storeName, List<String> recipients) {
        String loginUrl = frontendUrl + "/login";
        String body = "<p style=\"margin:0 0 14px;\">Hola,</p>"
                + "<p style=\"margin:0 0 14px;\">¡Buenas noticias! Revisamos la información de <strong>"
                + EmailTemplate.escape(storeName) + "</strong> y tu local fue <strong style=\"color:#16A34A;\">aprobado</strong>.</p>"
                + "<p style=\"margin:0;\">Ya puedes iniciar sesión y empezar a vender, controlar tu inventario y facturar con PosBento.</p>";
        String html = EmailTemplate.render(
                frontendUrl,
                "Tu local fue aprobado: ya puedes iniciar sesión en PosBento.",
                "¡Tu local fue aprobado!",
                body,
                "Iniciar sesión",
                loginUrl,
                "Si no reconoces este registro, puedes ignorar este mensaje.");
        for (String to : recipients) {
            try {
                emailSender.send(to, "Tu local fue aprobado - PosBento", html);
            } catch (RuntimeException e) {
                log.warn("No se pudo enviar el correo de aprobacion del local \"{}\" a {}", storeName, to, e);
            }
        }
    }

    /**
     * Avisa que el local fue rechazado, con el motivo, y que puede corregir los datos e iniciar sesión para
     * reenviarlo a revisión. Mismas garantías que {@link #sendApproved}: asíncrono y sin propagar errores.
     */
    @Async
    public void sendRejected(String storeName, String reason, List<String> recipients) {
        String loginUrl = frontendUrl + "/login";
        String body = "<p style=\"margin:0 0 14px;\">Hola,</p>"
                + "<p style=\"margin:0 0 14px;\">Revisamos la información de <strong>"
                + EmailTemplate.escape(storeName) + "</strong> y por ahora no pudimos aprobar tu local.</p>"
                + "<p style=\"margin:0 0 14px;\"><strong>Motivo:</strong> " + EmailTemplate.escape(reason) + "</p>"
                + "<p style=\"margin:0;\">Inicia sesión, corrige los datos de tu local y reenvíalo a revisión. "
                + "Te avisaremos por correo en cuanto lo revisemos de nuevo.</p>";
        String html = EmailTemplate.render(
                frontendUrl,
                "Tu local necesita correcciones antes de ser aprobado en PosBento.",
                "Tu local necesita correcciones",
                body,
                "Corregir mi local",
                loginUrl,
                "Si no reconoces este registro, puedes ignorar este mensaje.");
        for (String to : recipients) {
            try {
                emailSender.send(to, "Tu local necesita correcciones - PosBento", html);
            } catch (RuntimeException e) {
                log.warn("No se pudo enviar el correo de rechazo del local \"{}\" a {}", storeName, to, e);
            }
        }
    }
}
