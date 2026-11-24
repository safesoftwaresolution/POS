package com.sciencebot.pos.voicesales.internal.groq;

import com.sciencebot.pos.voicesales.internal.ports.SaleIntent;
import com.sciencebot.pos.voicesales.internal.ports.SaleIntentExtractor;
import com.sciencebot.pos.voicesales.internal.ports.SpeechTranscriber;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GroqVoiceConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(GroqVoiceConfiguration.class);

    @Test
    void disabledByDefaultWithoutAKey() {
        context.run(result -> {
            assertNull(result.getStartupFailure());
            assertTrue(result.getBeansOfType(SpeechTranscriber.class).isEmpty());
            assertTrue(result.getBeansOfType(SaleIntentExtractor.class).isEmpty());
        });
    }

    @Test
    void enablesBothDefaultProviders() {
        context.withPropertyValues("voice-sales.enabled=true").run(result -> {
            assertInstanceOf(GroqSpeechTranscriber.class, result.getBean(SpeechTranscriber.class));
            assertInstanceOf(GroqSaleIntentExtractor.class, result.getBean(SaleIntentExtractor.class));
        });
    }

    @Test
    void substitutesOnlyTranscriberIndependently() {
        SpeechTranscriber stub = (bytes, mediaType) -> "stub";
        context.withPropertyValues("voice-sales.enabled=true", "voice-sales.transcription-provider=stub")
                .withBean(SpeechTranscriber.class, () -> stub).run(result -> {
                    assertSame(stub, result.getBean(SpeechTranscriber.class));
                    assertInstanceOf(GroqSaleIntentExtractor.class, result.getBean(SaleIntentExtractor.class));
                });
    }

    @Test
    void substitutesOnlyExtractorIndependently() {
        SaleIntentExtractor stub = text -> new SaleIntent(List.of(), null, null, null);
        context.withPropertyValues("voice-sales.enabled=true", "voice-sales.extraction-provider=stub")
                .withBean(SaleIntentExtractor.class, () -> stub).run(result -> {
                    assertSame(stub, result.getBean(SaleIntentExtractor.class));
                    assertInstanceOf(GroqSpeechTranscriber.class, result.getBean(SpeechTranscriber.class));
                });
    }
}
