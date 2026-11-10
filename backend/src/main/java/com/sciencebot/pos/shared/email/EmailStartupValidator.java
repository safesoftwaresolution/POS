package com.sciencebot.pos.shared.email;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Falla el arranque si el correo está mal configurado: con {@code email.provider=smtp} sin remitente o sin
 * servidor, los correos de verificación y de aprobación fallarían en silencio y nadie podría completar el
 * registro. Un proveedor desconocido también se descubre al desplegar.
 */
@Component
public class EmailStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(EmailStartupValidator.class);
    private static final Set<String> PROVIDERS = Set.of("log", "smtp");

    private final String provider;
    private final String from;
    private final String smtpHost;

    public EmailStartupValidator(@Value("${email.provider:log}") String provider,
                                 @Value("${email.from:}") String from,
                                 @Value("${spring.mail.host:}") String smtpHost) {
        this.provider = provider == null ? "" : provider.trim().toLowerCase();
        this.from = from;
        this.smtpHost = smtpHost;
    }

    @PostConstruct
    public void validate() {
        if (!PROVIDERS.contains(provider)) {
            throw new IllegalStateException("email.provider='" + provider + "' no es válido. Usa 'log' o 'smtp' en EMAIL_PROVIDER.");
        }
        if ("smtp".equals(provider)) {
            List<String> missing = new ArrayList<>();
            if (from == null || from.isBlank()) {
                missing.add("EMAIL_FROM (email.from)");
            }
            if (smtpHost == null || smtpHost.isBlank()) {
                missing.add("SMTP_HOST (spring.mail.host)");
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException("EMAIL_PROVIDER=smtp requiere " + String.join(" y ", missing)
                        + ": sin eso no salen los correos de verificación ni de aprobación.");
            }
            log.info("Correo transaccional por SMTP ({}) desde {}", smtpHost, from);
        } else {
            log.warn("EMAIL_PROVIDER=log: los correos no se envían, solo se escriben en el log.");
        }
    }
}
