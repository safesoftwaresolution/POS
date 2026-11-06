package com.sciencebot.pos.shared;

import com.sciencebot.pos.shared.email.EmailStartupValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailStartupValidatorTest {

    @Test
    void startsWithTheLogProviderEvenWithoutSmtpSettings() {
        assertDoesNotThrow(new EmailStartupValidator("log", "", "")::validate);
    }

    @Test
    void startsWithSmtpWhenSenderAndHostAreSet() {
        assertDoesNotThrow(new EmailStartupValidator("smtp", "no-reply@posbento.com", "smtp.example.com")::validate);
    }

    @Test
    void failsToStartWithSmtpAndABlankSenderOrHost() {
        IllegalStateException noFrom = assertThrows(IllegalStateException.class,
                new EmailStartupValidator("smtp", " ", "smtp.example.com")::validate);
        assertTrue(noFrom.getMessage().contains("EMAIL_FROM"), noFrom.getMessage());

        IllegalStateException noHost = assertThrows(IllegalStateException.class,
                new EmailStartupValidator("smtp", "no-reply@posbento.com", null)::validate);
        assertTrue(noHost.getMessage().contains("SMTP_HOST"), noHost.getMessage());
    }

    @Test
    void failsToStartWithAnUnknownProvider() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                new EmailStartupValidator("smpt", "no-reply@posbento.com", "smtp.example.com")::validate);
        assertTrue(ex.getMessage().contains("smpt"));
        assertTrue(ex.getMessage().contains("EMAIL_PROVIDER"));
    }
}
