package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.config.security.PosUserDetails;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.voicesales.VoiceAgentRequest;
import com.sciencebot.pos.voicesales.internal.agent.HarnessCases.HarnessCase;
import com.sciencebot.pos.voicesales.internal.ports.SpeechTranscriber;
import com.sciencebot.pos.voicesales.internal.resolution.VoiceSaleResolver;
import com.sciencebot.pos.voicesales.internal.services.VoicePreviewQuota;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Deterministic harness: the real service, tools, resolver and tool loop run against a small fixture store, with a
 * scripted model standing in for the provider. It proves the harness turns what a good model does into the right
 * ticket, and that every case's expectation is satisfiable; {@link AgentHarnessLiveEval} asks a real model.
 */
class AgentHarnessScriptedTest {
    private final ProductFacade products = mock(ProductFacade.class);
    private final ServiceFacade services = mock(ServiceFacade.class);
    private final CustomerFacade customers = mock(CustomerFacade.class);
    private final SettingsFacade settings = mock(SettingsFacade.class);

    @BeforeEach
    void setUp() {
        var principal = new PosUserDetails("harness", "unused", true,
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

    static Stream<HarnessCase> cases() {
        return HarnessCases.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void aGoodModelLeavesTheExpectedTicket(HarnessCase scenario) {
        var chat = new ScriptedChatModel(scenario.script());
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chat);
        var service = new VoiceAgentService(new SpringAiAgentModel(provider, 6), mock(SpeechTranscriber.class),
                new VoiceSaleResolver(products, services, customers, settings), products, services, customers,
                settings, new VoicePreviewQuota(100, 100, Clock.systemUTC()));

        var response = service.turn(new VoiceAgentRequest(scenario.utterance(), scenario.start(), List.of()));

        scenario.expect().accept(response);
        assertThat(response.reply()).isNotBlank();
    }

    @Test
    void theHarnessCoversTheBehaviorsTheFeatureIsAbout() {
        assertThat(HarnessCases.all()).extracting(HarnessCase::name).hasSize(10).doesNotHaveDuplicates();
    }
}
