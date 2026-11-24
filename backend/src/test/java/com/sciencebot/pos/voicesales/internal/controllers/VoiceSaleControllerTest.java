package com.sciencebot.pos.voicesales.internal.controllers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sciencebot.pos.voicesales.VoiceSaleFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VoiceSaleControllerTest {
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(VoiceSaleController.class);

    @BeforeEach
    void attachAppender() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(logs);
    }

    @Test
    void unexpectedFailuresAreLoggedByClassNameOnly() {
        var controller = new VoiceSaleController(mock(VoiceSaleFacade.class));

        var response = controller.unexpected(new IllegalStateException("Ana Pérez 1234"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).containsEntry("message", "No se pudo preparar el borrador.");
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("IllegalStateException").doesNotContain("Ana");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}
