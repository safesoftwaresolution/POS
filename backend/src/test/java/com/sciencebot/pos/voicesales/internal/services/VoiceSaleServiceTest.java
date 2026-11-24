package com.sciencebot.pos.voicesales.internal.services;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sciencebot.pos.config.security.PosUserDetails;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.voicesales.VoiceSalePreviewDto;
import com.sciencebot.pos.voicesales.internal.ports.SaleDraftResolver;
import com.sciencebot.pos.voicesales.internal.ports.SaleIntent;
import com.sciencebot.pos.voicesales.internal.ports.SaleIntentExtractor;
import com.sciencebot.pos.voicesales.internal.ports.SpeechTranscriber;
import com.sciencebot.pos.voicesales.internal.ports.VoiceProviderException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VoiceSaleServiceTest {
    private static final byte[] WAV = "RIFF0000WAVEfmt fixture".getBytes(StandardCharsets.US_ASCII);
    private final SpeechTranscriber transcriber = mock(SpeechTranscriber.class);
    private final SaleIntentExtractor extractor = mock(SaleIntentExtractor.class);
    private final SaleDraftResolver resolver = mock(SaleDraftResolver.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(VoiceSaleService.class);
    private VoiceSaleService service;

    @BeforeEach
    void setUp() {
        service = new VoiceSaleService(transcriber, extractor, resolver, new VoicePreviewQuota(100, 100, Clock.systemUTC()));
        var principal = new PosUserDetails("caja", "unused", true,
                List.of(new SimpleGrantedAuthority("ROLE_SELLER")), 5L, "ACTIVE");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        TenantContext.setStoreId(5L);
        logs.start();
        logger.addAppender(logs);
        when(transcriber.transcribe(any(), eq("audio/wav"))).thenReturn("texto dictado");
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void intentsOutsideTheProviderContractAreRejectedBeforeResolving() {
        List<SaleIntent> outside = List.of(
                intent(items(31, 1), null, null, null),
                intent(items(1, 1001), null, null, null),
                intent(items(1, 0), null, null, null),
                intent(items(1, -2), null, null, null),
                intent(List.of(new SaleIntent.Item("a".repeat(201), 1)), null, null, null),
                intent(items(1, 1), "a".repeat(201), null, null),
                intent(items(1, 1), null, "a".repeat(101), null),
                intent(items(1, 1), null, "efectivo", new BigDecimal("-1")),
                intent(items(1, 1), null, "efectivo", new BigDecimal("10.001")),
                intent(items(1, 1), null, "efectivo", new BigDecimal("1000000000000.00")));

        for (SaleIntent bad : outside) {
            when(extractor.extract("texto dictado")).thenReturn(bad);
            assertThatThrownBy(this::preview).isInstanceOf(VoiceProviderException.class);
        }
        verifyNoInteractions(resolver);
    }

    @Test
    void intentsAtTheContractLimitsReachTheResolver() {
        SaleIntent atLimit = intent(items(30, 1000), "a".repeat(200), "a".repeat(100), new BigDecimal("999999999999.99"));
        var draft = new VoiceSalePreviewDto(List.of(), null, null, null, List.of());
        when(extractor.extract("texto dictado")).thenReturn(atLimit);
        when(resolver.resolve(atLimit)).thenReturn(draft);

        assertThat(preview()).isSameAs(draft);
        verify(resolver, times(1)).resolve(atLimit);
    }

    @Test
    void providerFailuresAreLoggedByClassNameOnly() {
        when(transcriber.transcribe(any(), anyString())).thenThrow(new IllegalStateException("sensitive fixture text"));

        assertThatThrownBy(this::preview).isInstanceOf(VoiceProviderException.class);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("IllegalStateException").doesNotContain("sensitive");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    private VoiceSalePreviewDto preview() {
        return service.preview(new ByteArrayInputStream(WAV), "audio/wav", WAV.length);
    }

    private static SaleIntent intent(List<SaleIntent.Item> items, String customer, String payment, BigDecimal cash) {
        return new SaleIntent(items, customer, payment, cash);
    }

    private static List<SaleIntent.Item> items(int count, int quantity) {
        return IntStream.range(0, count).mapToObj(i -> new SaleIntent.Item("café " + i, quantity)).toList();
    }
}
