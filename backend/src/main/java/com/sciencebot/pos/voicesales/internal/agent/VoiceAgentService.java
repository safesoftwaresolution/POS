package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.voicesales.VoiceAgentFacade;
import com.sciencebot.pos.voicesales.VoiceAgentRequest;
import com.sciencebot.pos.voicesales.VoiceAgentResponse;
import com.sciencebot.pos.voicesales.internal.ports.AgentModel;
import com.sciencebot.pos.voicesales.internal.ports.SaleDraftResolver;
import com.sciencebot.pos.voicesales.internal.ports.SpeechTranscriber;
import com.sciencebot.pos.voicesales.internal.ports.VoiceProviderException;
import com.sciencebot.pos.voicesales.internal.services.PreviewRejected;
import com.sciencebot.pos.voicesales.internal.services.VoiceAudio;
import com.sciencebot.pos.voicesales.internal.services.VoicePreviewQuota;
import com.sciencebot.pos.voicesales.internal.services.VoiceSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;

/**
 * Orchestrates one agent turn. No transaction encloses the provider call, the model only ever sees the cashier's
 * words plus a short history, and nothing in this class can register a sale: the POS confirms on its own through
 * the existing sales API.
 */
@Service
@ConditionalOnProperty(name = {"voice-sales.enabled", "voice-sales.agent.enabled"}, havingValue = "true")
final class VoiceAgentService implements VoiceAgentFacade {
    private static final Logger log = LoggerFactory.getLogger(VoiceAgentService.class);
    private static final int MAX_REPLY = 800;

    private final AgentModel model;
    private final SpeechTranscriber transcriber;
    private final SaleDraftResolver resolver;
    private final ProductFacade products;
    private final ServiceFacade services;
    private final CustomerFacade customers;
    private final TicketRestorer restorer;
    private final VoicePreviewQuota quota;

    @Autowired
    VoiceAgentService(AgentModel model, SpeechTranscriber transcriber, SaleDraftResolver resolver,
                      ProductFacade products, ServiceFacade services, CustomerFacade customers,
                      SettingsFacade settings,
                      @Value("${voice-sales.agent.user-per-minute:30}") int userLimit,
                      @Value("${voice-sales.agent.store-per-minute:120}") int storeLimit) {
        this(model, transcriber, resolver, products, services, customers, settings,
                new VoicePreviewQuota(userLimit, storeLimit, Clock.systemUTC()));
    }

    VoiceAgentService(AgentModel model, SpeechTranscriber transcriber, SaleDraftResolver resolver,
                      ProductFacade products, ServiceFacade services, CustomerFacade customers,
                      SettingsFacade settings, VoicePreviewQuota quota) {
        this.model = model;
        this.transcriber = transcriber;
        this.resolver = resolver;
        this.products = products;
        this.services = services;
        this.customers = customers;
        this.restorer = new TicketRestorer(products, services, customers, settings);
        this.quota = quota;
    }

    @Override
    public String transcribe(InputStream input, String mediaType, long contentLength) {
        var actor = VoiceSession.require();
        String type = VoiceAudio.type(mediaType);
        byte[] audio = VoiceAudio.read(input, type, contentLength);
        try {
            quota.acquire(actor.store(), actor.username());
            String text = transcriber.transcribe(audio, type);
            if (text == null || text.isBlank()) {
                throw new VoiceProviderException();
            }
            return text.trim();
        } catch (PreviewRejected | VoiceProviderException rejected) {
            throw rejected;
        } catch (RuntimeException failure) {
            // Class name only: messages and causes may carry the dictated text or provider content.
            log.warn("Voice agent transcription failure: {}", failure.getClass().getName());
            throw new VoiceProviderException();
        } finally {
            Arrays.fill(audio, (byte) 0);
        }
    }

    @Override
    public VoiceAgentResponse turn(VoiceAgentRequest request) {
        var actor = VoiceSession.require();
        String text = request == null || request.text() == null ? "" : request.text().trim();
        if (text.isEmpty() || text.length() > VoiceAgentRequest.MAX_TEXT) {
            throw new PreviewRejected(HttpStatus.BAD_REQUEST,
                    "Dime qué necesitas, en menos de " + VoiceAgentRequest.MAX_TEXT + " caracteres.");
        }
        quota.acquire(actor.store(), actor.username());

        var draft = new TicketDraft();
        List<String> warnings = restorer.restore(draft, request.ticket());
        var tools = new TicketTools(draft, resolver, products, services, customers);
        String reply = runModel(text, history(request.history()), tools);
        return new VoiceAgentResponse(reply, tools.ticket(), List.copyOf(warnings));
    }

    private String runModel(String text, List<VoiceAgentRequest.Turn> history, TicketTools tools) {
        try {
            String reply = model.run(AgentPrompt.SYSTEM, history, text, tools);
            reply = reply == null ? "" : reply.strip();
            return reply.length() > MAX_REPLY ? reply.substring(0, MAX_REPLY) : reply;
        } catch (RuntimeException failure) {
            // Class name only: messages and causes may carry the dictated text or provider content.
            log.warn("Voice agent provider failure: {}", failure.getClass().getName());
            // Opt-in diagnosis only (logging.level for this class at DEBUG): the message may echo content.
            log.debug("Voice agent provider failure detail", failure);
            throw new VoiceProviderException();
        }
    }

    /** Last few turns, user/assistant only: a client cannot inject a system message or an unbounded history. */
    private static List<VoiceAgentRequest.Turn> history(List<VoiceAgentRequest.Turn> turns) {
        if (turns == null) {
            return List.of();
        }
        var clean = turns.stream()
                .filter(t -> t != null && t.text() != null && !t.text().isBlank()
                        && ("user".equals(t.role()) || "assistant".equals(t.role())))
                .map(t -> new VoiceAgentRequest.Turn(t.role(), t.text().length() > VoiceAgentRequest.MAX_TEXT
                        ? t.text().substring(0, VoiceAgentRequest.MAX_TEXT) : t.text()))
                .toList();
        return clean.size() <= VoiceAgentRequest.MAX_HISTORY ? clean
                : clean.subList(clean.size() - VoiceAgentRequest.MAX_HISTORY, clean.size());
    }
}
