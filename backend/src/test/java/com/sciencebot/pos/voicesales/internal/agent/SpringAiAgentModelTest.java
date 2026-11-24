package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.voicesales.VoiceAgentRequest;
import com.sciencebot.pos.voicesales.internal.ports.VoiceProviderException;
import com.sciencebot.pos.voicesales.internal.resolution.VoiceSaleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.ai.chat.model.ChatModel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.sciencebot.pos.voicesales.internal.agent.ScriptedChatModel.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiAgentModelTest {
    private final ProductFacade products = mock(ProductFacade.class);
    private final ServiceFacade services = mock(ServiceFacade.class);
    private final CustomerFacade customers = mock(CustomerFacade.class);
    private final SettingsFacade settings = mock(SettingsFacade.class);
    private TicketDraft draft;
    private TicketTools tools;

    @BeforeEach
    void setUp() {
        com.sciencebot.pos.config.tenant.TenantContext.setStoreId(7L);
        when(products.searchProducts(anyString(), isNull(), eq(true), any())).thenReturn(Page.empty());
        when(services.searchServices(anyString(), isNull(), eq(true), any())).thenReturn(Page.empty());
        when(customers.searchCustomers(anyString(), any())).thenReturn(Page.empty());
        when(settings.getActiveValuesByTopic("PAYMENT_METHODS")).thenReturn(List.of());
        var coffee = new ProductDto(11L, "P-11", null, "Café grande", null, null, null,
                BigDecimal.ONE, new BigDecimal("3500"), 40, 0, true, null);
        when(products.searchProducts(eq("Café grande"), isNull(), eq(true), any())).thenReturn(new PageImpl<>(List.of(coffee)));
        when(products.getById(11L)).thenReturn(Optional.of(coffee));
        draft = new TicketDraft();
        tools = new TicketTools(draft, new VoiceSaleResolver(products, services, customers, settings), products, services, customers);
    }

    @org.junit.jupiter.api.AfterEach
    void clear() {
        com.sciencebot.pos.config.tenant.TenantContext.clear();
    }

    private static SpringAiAgentModel agent(ChatModel model, int maxIterations) {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        return new SpringAiAgentModel(provider, maxIterations);
    }

    @Test
    void theLoopExecutesTheModelsToolCallsAgainstTheTicketAndReturnsTheFinalText() {
        var model = new ScriptedChatModel(
                calls(call("agregarArticulo", "{\"nombre\":\"Café grande\",\"cantidad\":2}"), call("pedirConfirmacion", "{}")),
                say("Listo: 2 cafés. ¿Confirmas?"));

        String reply = agent(model, 6).run("sistema", List.of(new VoiceAgentRequest.Turn("user", "hola")),
                "dos cafés grandes y cobra", tools);

        assertThat(reply).isEqualTo("Listo: 2 cafés. ¿Confirmas?");
        assertThat(draft.lines()).hasSize(1);
        assertThat(draft.lines().getFirst().quantity()).isEqualTo(2);
        assertThat(draft.confirmationRequested()).isTrue();
        assertThat(model.prompts()).hasSize(2);
        // The second round carries the tool results back to the model.
        assertThat(model.prompts().get(1).getInstructions().toString()).contains("Agregado");
    }

    @Test
    void theMessagesTheModelSeesAreOnlySystemHistoryAndTheCurrentText() {
        var model = new ScriptedChatModel(say("ok"));

        agent(model, 6).run("SISTEMA-X", List.of(new VoiceAgentRequest.Turn("user", "antes"),
                new VoiceAgentRequest.Turn("assistant", "respuesta previa")), "ahora", tools);

        var messages = model.prompts().getFirst().getInstructions();
        assertThat(messages).extracting(m -> m.getMessageType().name()).containsExactly("SYSTEM", "USER", "ASSISTANT", "USER");
        assertThat(messages.getFirst().getText()).isEqualTo("SISTEMA-X");
        assertThat(messages.getLast().getText()).isEqualTo("ahora");
    }

    @Test
    void aModelThatNeverStopsAskingForToolsIsCutOffAtTheConfiguredBound() {
        var model = new ScriptedChatModel(calls(call("vaciarTicket", "{}")));

        String reply = agent(model, 3).run("s", List.of(), "x", tools);

        assertThat(model.prompts()).hasSize(3);
        assertThat(reply).contains("Revisa el ticket");
    }

    @Test
    void anInvalidToolCallDoesNotChangeTheTicketAndTheModelIsToldWhy() {
        var model = new ScriptedChatModel(
                calls(call("agregarArticulo", "{\"nombre\":\"Café grande\",\"cantidad\":99999}")),
                say("No pude agregarlo."));

        agent(model, 6).run("s", List.of(), "mil cafés", tools);

        assertThat(draft.lines()).isEmpty();
        assertThat(model.prompts().get(1).getInstructions().toString()).contains("La cantidad debe estar entre");
    }

    @Test
    void withoutAChatModelTheAgentIsUnavailableInsteadOfFailingAtBoot() {
        assertThatThrownBy(() -> agent(null, 6).run("s", List.of(), "x", tools)).isInstanceOf(VoiceProviderException.class);
    }
}
