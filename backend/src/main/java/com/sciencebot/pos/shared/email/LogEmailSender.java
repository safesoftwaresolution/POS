package com.sciencebot.pos.shared.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Implementacion para desarrollo/pruebas: no envia correo real, solo lo deja en el log.
 * Es el default (matchIfMissing) para que el entorno dev no requiera credenciales SMTP.
 */
@Service
@ConditionalOnProperty(prefix = "email", name = "provider", havingValue = "log", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void send(String to, String subject, String htmlBody) {
        log.info("==================================================");
        log.info("[EMAIL SIMULADO] Para: {}", to);
        log.info("[EMAIL SIMULADO] Asunto: {}", subject);
        log.info("[EMAIL SIMULADO] Cuerpo:\n{}", htmlBody);
        log.info("==================================================");
    }
}
