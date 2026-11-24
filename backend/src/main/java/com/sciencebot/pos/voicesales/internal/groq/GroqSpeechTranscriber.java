package com.sciencebot.pos.voicesales.internal.groq;

import com.sciencebot.pos.voicesales.internal.ports.SpeechTranscriber;
import com.sciencebot.pos.voicesales.internal.ports.VoiceProviderException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;

final class GroqSpeechTranscriber implements SpeechTranscriber {
    private static final int MAX_AUDIO_BYTES = 5 * 1024 * 1024;
    private final GroqTransport transport;
    private final String model;

    GroqSpeechTranscriber(GroqTransport transport, String model) {
        this.transport = transport;
        this.model = model;
    }

    @Override
    public String transcribe(byte[] audio, String mediaType) {
        try {
            if (audio == null || audio.length == 0 || audio.length > MAX_AUDIO_BYTES
                    || !("audio/webm".equals(mediaType) || "audio/wav".equals(mediaType))) {
                throw new VoiceProviderException();
            }
            GroqSaleIntentExtractor.requireText(model, 200);
            var file = new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return "audio/wav".equals(mediaType) ? "audio.wav" : "audio.webm";
                }
            };
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(mediaType));
            var multipart = new LinkedMultiValueMap<String, Object>();
            multipart.add("file", new HttpEntity<>(file, headers));
            multipart.add("model", model);
            multipart.add("language", "es");
            multipart.add("response_format", "json");
            var response = transport.transcribe(multipart);
            var text = response == null ? null : response.get("text");
            if (text == null || !text.isString()) {
                throw new VoiceProviderException();
            }
            return GroqSaleIntentExtractor.requireText(text.asString(), 12_000);
        } catch (Exception ignored) {
            throw new VoiceProviderException();
        }
    }
}
