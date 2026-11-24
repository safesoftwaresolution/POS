package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.config.security.PosUserDetails;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.voicesales.VoiceAgentRequest;
import com.sciencebot.pos.voicesales.internal.ports.SaleIntentExtractor;
import com.sciencebot.pos.voicesales.internal.ports.SpeechTranscriber;
import com.sciencebot.pos.voicesales.internal.resolution.VoiceSaleResolver;
import com.sciencebot.pos.voicesales.internal.services.VoicePreviewQuota;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Runs the agent through the real Spring AI OpenAI client against a local server that speaks the OpenAI chat
 * protocol. The scripted model cannot catch what only the real client does (it once failed here with a
 * ClassCastException on the prompt options), and unlike the live evaluation it needs no key or internet.
 */
@SpringBootTest(properties = {"voice-sales.enabled=true", "voice-sales.agent.enabled=true",
        "voice-sales.transcription-provider=fixture", "voice-sales.extraction-provider=fixture",
        "spring.ai.model.chat=openai", "spring.ai.openai.api-key=test-key",
        "spring.ai.openai.chat.model=fixture-model", "spring.ai.openai.chat.temperature=0.1",
        "spring.datasource.url=jdbc:h2:mem:voice_agent_protocol;MODE=PostgreSQL", "spring.jpa.show-sql=false"})
class AgentOpenAiProtocolTest {
    private static final List<String> BODIES = new CopyOnWriteArrayList<>();
    private static final List<String> AUTH = new CopyOnWriteArrayList<>();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static HttpServer server;

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry registry) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            BODIES.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            AUTH.add(exchange.getRequestHeaders().getFirst("Authorization"));
            String json = CALLS.getAndIncrement() == 0 ? TOOL_CALL : FINAL_ANSWER;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    }

    private static final String TOOL_CALL = """
            {"id":"c1","object":"chat.completion","created":1,"model":"fixture-model","choices":[{"index":0,
            "finish_reason":"tool_calls","message":{"role":"assistant","content":null,"reasoning":"pienso en voz alta","reasoning_content":"pienso en voz alta","tool_calls":[
            {"id":"call_1","type":"function","function":{"name":"agregarArticulo",
            "arguments":"{\\"nombre\\":\\"Café grande\\",\\"cantidad\\":2}"}}]}}],
            "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}""";
    private static final String FINAL_ANSWER = """
            {"id":"c2","object":"chat.completion","created":2,"model":"fixture-model","choices":[{"index":0,
            "finish_reason":"stop","message":{"role":"assistant","content":"Listo: 2 cafés grandes."}}],
            "usage":{"prompt_tokens":20,"completion_tokens":6,"total_tokens":26}}""";

    @Autowired ObjectProvider<ChatModel> chatModel;
    @MockitoBean SpeechTranscriber transcriber;
    @MockitoBean SaleIntentExtractor extractor;
    private final ProductFacade products = mock(ProductFacade.class);
    private final ServiceFacade services = mock(ServiceFacade.class);
    private final CustomerFacade customers = mock(CustomerFacade.class);
    private final SettingsFacade settings = mock(SettingsFacade.class);

    @BeforeEach
    void setUp() {
        BODIES.clear();
        AUTH.clear();
        CALLS.set(0);
        var principal = new PosUserDetails("protocol", "unused", true,
                List.of(new SimpleGrantedAuthority("ROLE_SELLER")), 5L, "ACTIVE");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        TenantContext.setStoreId(5L);
        HarnessFixture.stub(products, services, customers, settings);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void theRealClientSendsToolsAndTheModelOptionsAndExecutesTheToolCallItGets() {
        var service = new VoiceAgentService(new SpringAiAgentModel(chatModel, 6), transcriber,
                new VoiceSaleResolver(products, services, customers, settings), products, services, customers,
                settings, new VoicePreviewQuota(100, 100, Clock.systemUTC()));

        var response = service.turn(new VoiceAgentRequest("dos cafés grandes", null, List.of()));

        assertThat(response.reply()).isEqualTo("Listo: 2 cafés grandes.");
        assertThat(response.ticket().lines()).singleElement().satisfies(line -> {
            assertThat(line.name()).isEqualTo("Café grande");
            assertThat(line.quantity()).isEqualTo(2);
            assertThat(line.unitPrice()).isEqualByComparingTo("3500");
        });
        assertThat(BODIES).hasSize(2);
        assertThat(AUTH).containsOnly("Bearer test-key");
        // Round one carries the configured model, the system prompt, the user's words and every tool definition.
        assertThat(BODIES.getFirst()).contains("fixture-model").contains("asistente de caja").contains("dos cafés grandes")
                .contains("agregarArticulo").contains("pedirConfirmacion").contains("vaciarTicket");
        // Round two carries the tool result back; nothing about the store or user ever travels.
        assertThat(BODIES.get(1)).contains("call_1").contains("Agregado");
        // Reasoning models return their chain of thought; sending it back makes Groq answer 400.
        assertThat(BODIES.get(1)).doesNotContain("pienso en voz alta").doesNotContain("reasoning");
        assertThat(String.join("", BODIES)).doesNotContain("protocol").doesNotContain("ACTIVE");
    }
}
