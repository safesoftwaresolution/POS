package com.sciencebot.pos.voicesales.internal.groq;

import com.sciencebot.pos.config.http.OutboundHttp;
import com.sciencebot.pos.voicesales.internal.ports.SaleIntentExtractor;
import com.sciencebot.pos.voicesales.internal.ports.SpeechTranscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Each port has its own provider selector; no transport or provider beans exist while disabled. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "voice-sales.enabled", havingValue = "true")
class GroqVoiceConfiguration {
    @Bean
    @ConditionalOnMissingBean(SpeechTranscriber.class)
    @ConditionalOnProperty(name = "voice-sales.transcription-provider", havingValue = "groq", matchIfMissing = true)
    SpeechTranscriber groqSpeechTranscriber(@Value("${voice-sales.groq.api-key:}") String apiKey,
            @Value("${voice-sales.groq.transcription-model:whisper-large-v3-turbo}") String model) {
        return new GroqSpeechTranscriber(new GroqTransport(OutboundHttp.noRedirectsRestClient(), apiKey), model);
    }

    @Bean
    @ConditionalOnMissingBean(SaleIntentExtractor.class)
    @ConditionalOnProperty(name = "voice-sales.extraction-provider", havingValue = "groq", matchIfMissing = true)
    SaleIntentExtractor groqSaleIntentExtractor(@Value("${voice-sales.groq.api-key:}") String apiKey,
            @Value("${voice-sales.groq.extraction-model:openai/gpt-oss-20b}") String model) {
        return new GroqSaleIntentExtractor(new GroqTransport(OutboundHttp.noRedirectsRestClient(), apiKey), model);
    }
}
