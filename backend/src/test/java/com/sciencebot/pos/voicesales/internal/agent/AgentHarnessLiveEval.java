package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.config.security.PosUserDetails;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.voicesales.VoiceAgentFacade;
import com.sciencebot.pos.voicesales.VoiceAgentRequest;
import com.sciencebot.pos.voicesales.internal.agent.HarnessCases.HarnessCase;
import com.sciencebot.pos.voicesales.internal.ports.SaleIntentExtractor;
import com.sciencebot.pos.voicesales.internal.ports.SpeechTranscriber;
import com.sciencebot.pos.voicesales.internal.resolution.VoiceSaleResolver;
import com.sciencebot.pos.voicesales.internal.services.VoicePreviewQuota;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Opt-in evaluation against the real provider. It never runs in the normal build: it needs both
 * {@code VOICE_SALES_LIVE_EVAL=true} and a {@code GROQ_API_KEY} in the environment (use a test key; each run makes
 * a handful of model calls with fictitious data from {@link HarnessFixture}).
 *
 * <pre>
 *   $env:VOICE_SALES_LIVE_EVAL = "true"; $env:GROQ_API_KEY = "&lt;test key&gt;"
 *   .\mvnw.cmd test "-Dtest=AgentHarnessLiveEval"
 * </pre>
 *
 * The model, base URL and the pass-rate floor come from {@code VOICE_SALES_AGENT_MODEL},
 * {@code VOICE_SALES_AGENT_BASE_URL} and {@code VOICE_SALES_LIVE_MIN_PASS_RATE} (default 0.8). Models are not
 * deterministic, so the floor is a rate, and the per-case outcome is printed.
 */
@EnabledIfEnvironmentVariable(named = "VOICE_SALES_LIVE_EVAL", matches = "true")
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
@SpringBootTest(properties = {"voice-sales.enabled=true", "voice-sales.agent.enabled=true",
        "voice-sales.transcription-provider=fixture", "voice-sales.extraction-provider=fixture",
        "spring.ai.model.chat=openai", "spring.ai.openai.api-key=${GROQ_API_KEY}",
        "spring.ai.openai.base-url=${VOICE_SALES_AGENT_BASE_URL:https://api.groq.com/openai/v1}",
        "spring.ai.openai.chat.model=${VOICE_SALES_AGENT_MODEL:openai/gpt-oss-20b}",
        "spring.ai.openai.chat.temperature=0.1",
        "spring.datasource.url=jdbc:h2:mem:voice_agent_live;MODE=PostgreSQL", "spring.jpa.show-sql=false"})
class AgentHarnessLiveEval {
    @Autowired ObjectProvider<ChatModel> chatModel;
    private final ProductFacade products = mock(ProductFacade.class);
    private final ServiceFacade services = mock(ServiceFacade.class);
    private final CustomerFacade customers = mock(CustomerFacade.class);
    private final SettingsFacade settings = mock(SettingsFacade.class);
    private VoiceAgentFacade agent;
    @MockitoBean SpeechTranscriber transcriber;
    @MockitoBean SaleIntentExtractor extractor;

    @BeforeEach
    void setUp() {
        var principal = new PosUserDetails("live-eval", "unused", true,
                List.of(new SimpleGrantedAuthority("ROLE_SELLER")), 5L, "ACTIVE");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        TenantContext.setStoreId(5L);
        HarnessFixture.stub(products, services, customers, settings);
        agent = new VoiceAgentService(new SpringAiAgentModel(chatModel, 6), transcriber,
                new VoiceSaleResolver(products, services, customers, settings), products, services, customers,
                settings, new VoicePreviewQuota(100, 100, Clock.systemUTC()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void theRealModelLeavesTheExpectedTicketInMostCases() {
        double floor = Double.parseDouble(System.getenv().getOrDefault("VOICE_SALES_LIVE_MIN_PASS_RATE", "0.8"));
        List<String> report = new ArrayList<>();
        int passed = 0;
        for (HarnessCase scenario : HarnessCases.all()) {
            try {
                var response = agent.turn(new VoiceAgentRequest(scenario.utterance(), scenario.start(), List.of()));
                scenario.expect().accept(response);
                passed++;
                report.add("PASS  " + scenario.name());
            } catch (AssertionError | RuntimeException failure) {
                report.add("FAIL  " + scenario.name() + " -> " + firstLine(failure));
            }
        }
        System.out.println("Live agent evaluation: " + passed + "/" + HarnessCases.all().size());
        report.forEach(System.out::println);
        assertThat((double) passed / HarnessCases.all().size()).as(String.join("\n", report)).isGreaterThanOrEqualTo(floor);
    }

    private static String firstLine(Throwable failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return message.lines().findFirst().orElse(message);
    }
}
