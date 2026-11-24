package com.sciencebot.pos.voicesales.internal.services;

import com.sciencebot.pos.voicesales.VoiceSaleFacade;
import com.sciencebot.pos.voicesales.VoiceSalePreviewDto;
import com.sciencebot.pos.voicesales.internal.ports.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Arrays;

@Service
@ConditionalOnProperty(name = "voice-sales.enabled", havingValue = "true")
class VoiceSaleService implements VoiceSaleFacade {
    private static final Logger log = LoggerFactory.getLogger(VoiceSaleService.class);
    private final SpeechTranscriber transcriber;
    private final SaleIntentExtractor extractor;
    private final SaleDraftResolver resolver;
    private final VoicePreviewQuota quota;

    VoiceSaleService(SpeechTranscriber transcriber, SaleIntentExtractor extractor,
                     SaleDraftResolver resolver, VoicePreviewQuota quota) {
        this.transcriber = transcriber;
        this.extractor = extractor;
        this.resolver = resolver;
        this.quota = quota;
    }

    @Override
    public VoiceSalePreviewDto preview(InputStream input, String mediaType, long contentLength) {
        var actor = VoiceSession.require();
        String type = VoiceAudio.type(mediaType);
        byte[] audio = VoiceAudio.read(input, type, contentLength);
        SaleIntent intent;
        try {
            quota.acquire(actor.store(), actor.username());
            intent = extract(audio, type);
        } finally {
            Arrays.fill(audio, (byte) 0);
        }
        return resolver.resolve(intent);
    }

    // No transaction encloses the outbound calls. A provider failure must not leak content.
    private SaleIntent extract(byte[] audio, String type) {
        try {
            String transcript = transcriber.transcribe(audio, type);
            if (transcript == null || transcript.isBlank()) throw new VoiceProviderException();
            SaleIntent intent = extractor.extract(transcript);
            if (intent == null || !intent.withinContract()) throw new VoiceProviderException();
            return intent;
        } catch (RuntimeException failure) {
            // Class name only: messages and causes may carry the dictated text or provider content.
            log.warn("Voice preview provider failure: {}", failure.getClass().getName());
            throw new VoiceProviderException();
        }
    }
}
