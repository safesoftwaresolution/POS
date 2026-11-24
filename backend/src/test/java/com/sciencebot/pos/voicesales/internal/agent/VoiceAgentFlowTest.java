package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.config.security.JwtService;
import com.sciencebot.pos.config.security.PosUserDetails;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.products.internal.entities.Product;
import com.sciencebot.pos.products.internal.repositories.ProductRepository;
import com.sciencebot.pos.sales.SaleCompletedEvent;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import com.sciencebot.pos.voicesales.internal.ports.SaleIntentExtractor;
import com.sciencebot.pos.voicesales.internal.ports.SpeechTranscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static com.sciencebot.pos.voicesales.internal.agent.ScriptedChatModel.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"voice-sales.enabled=true", "voice-sales.agent.enabled=true",
        "voice-sales.transcription-provider=fixture", "voice-sales.extraction-provider=fixture",
        "spring.datasource.url=jdbc:h2:mem:voice_agent_flow;MODE=PostgreSQL", "spring.jpa.show-sql=false"})
@RecordApplicationEvents
class VoiceAgentFlowTest {
    private static final String TURN = "/api/v1/voice-sales/agent/turn";
    private static final String TRANSCRIBE = "/api/v1/voice-sales/agent/transcribe";
    private static final byte[] WAV = "RIFF0000WAVEfmt fixture".getBytes(StandardCharsets.US_ASCII);
    @Autowired WebApplicationContext context;
    @Autowired ProductRepository products;
    @Autowired JwtService tokens;
    @Autowired JdbcClient db;
    @Autowired ApplicationEvents events;
    @MockitoBean UserDetailsService identities;
    @MockitoBean UserFacade users;
    @MockitoBean SpeechTranscriber transcriber;
    @MockitoBean SaleIntentExtractor extractor;
    @MockitoBean ChatModel chat;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void script(ScriptedChatModel.Step... steps) {
        var model = new ScriptedChatModel(steps);
        when(chat.call(any(Prompt.class))).thenAnswer(invocation -> model.call(invocation.<Prompt>getArgument(0)));
    }

    @Test
    void theAgentFillsTheTicketAndAsksForConfirmationWithoutRegisteringAnything() throws Exception {
        String auth = cashier("agent-a", 51L, 51L);
        Long local = product(51L);
        script(calls(call("agregarArticulo", "{\"nombre\":\"Café grande\",\"cantidad\":2}"), call("pedirConfirmacion", "{}")),
                say("Listo: 2 cafés grandes. ¿Confirmas?"));

        long sales = count("sales"), moves = count("inventory_movements"), invoices = count("electronic_invoices");
        mvc.perform(post(TURN).header("Authorization", auth).contentType("application/json")
                        .content("{\"text\":\"dos cafés grandes y cobra\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.reply").value("Listo: 2 cafés grandes. ¿Confirmas?"))
                .andExpect(jsonPath("$.ticket.lines[0].id").value(local))
                .andExpect(jsonPath("$.ticket.lines[0].kind").value("PRODUCT"))
                .andExpect(jsonPath("$.ticket.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.ticket.lines[0].unitPrice").value(10))
                .andExpect(jsonPath("$.ticket.subtotal").value(20))
                .andExpect(jsonPath("$.ticket.confirmationRequested").value(true));

        assertThat(count("sales")).isEqualTo(sales);
        assertThat(count("inventory_movements")).isEqualTo(moves);
        assertThat(count("electronic_invoices")).isEqualTo(invoices);
        assertThat(products.findStockById(local)).isEqualTo(8);
        assertThat(events.stream(SaleCompletedEvent.class)).isEmpty();
    }

    @Test
    void aTicketLineFromAnotherStoreIsDroppedNotApplied() throws Exception {
        String auth = cashier("agent-b", 52L, 52L);
        Long foreign = product(53L);
        script(say("Sigo."));
        String body = "{\"text\":\"sigue\",\"ticket\":{\"lines\":[{\"kind\":\"PRODUCT\",\"id\":%d,\"quantity\":1}]}}"
                .formatted(foreign);

        mvc.perform(post(TURN).header("Authorization", auth).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.lines.length()").value(0))
                .andExpect(jsonPath("$.warnings.length()").value(1));
    }

    @Test
    void invalidRequestsAreRejectedBeforeTheModelIsCalled() throws Exception {
        String auth = cashier("agent-c", 54L, 54L);
        script(say("no debería llamarse"));

        mvc.perform(post(TURN).contentType("application/json").content("{\"text\":\"hola\"}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(post(TURN).header("Authorization", auth).contentType("application/json").content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest());
        String tooManyLines = "{\"text\":\"x\",\"ticket\":{\"lines\":[" + "{\"kind\":\"PRODUCT\",\"id\":1,\"quantity\":1},".repeat(101)
                + "{\"kind\":\"PRODUCT\",\"id\":1,\"quantity\":1}]}}";
        mvc.perform(post(TURN).header("Authorization", auth).contentType("application/json").content(tooManyLines))
                .andExpect(status().is(413));
        org.mockito.Mockito.verifyNoInteractions(chat);
    }

    @Test
    void providerErrorsArePrivate() throws Exception {
        String auth = cashier("agent-d", 55L, 55L);
        when(chat.call(any(Prompt.class))).thenThrow(new IllegalStateException("sensitive fixture text"));

        var result = mvc.perform(post(TURN).header("Authorization", auth).contentType("application/json")
                        .content("{\"text\":\"café para Ana Pérez\"}"))
                .andExpect(status().isServiceUnavailable()).andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("sensitive").doesNotContain("Ana");
    }

    @Test
    void transcribeReturnsOnlyTheText() throws Exception {
        String auth = cashier("agent-e", 56L, 56L);
        when(transcriber.transcribe(any(), eq("audio/wav"))).thenReturn("dos cafés grandes");

        mvc.perform(post(TRANSCRIBE).header("Authorization", auth).contentType("audio/wav").content(WAV))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.text").value("dos cafés grandes"));
        mvc.perform(post(TRANSCRIBE).header("Authorization", auth).contentType("audio/wav").content("not audio"))
                .andExpect(status().isUnsupportedMediaType());
    }

    private String cashier(String name, Long principalStore, Long jwtStore) {
        var principal = new PosUserDetails(name, "unused", true, List.of(new SimpleGrantedAuthority("ROLE_SELLER")),
                principalStore, "ACTIVE");
        when(identities.loadUserByUsername(name)).thenReturn(principal);
        var dto = new UserDto(104L, name, name, "fixture@example.invalid", "SELLER", true, principalStore, true);
        when(users.findByUsername(name)).thenReturn(Optional.of(dto));
        when(users.getById(104L)).thenReturn(dto);
        return "Bearer " + tokens.generateToken(principal, "SELLER", jwtStore);
    }

    private long count(String table) {
        return db.sql("select count(*) from " + table).query(Long.class).single();
    }

    private Long product(long store) {
        Product product = new Product();
        product.setName("Café grande");
        product.setInternalCode("CAFE-" + store);
        product.setStoreId(store);
        product.setCategoryId(1L);
        product.setQuantityAvailable(8);
        product.setSalePrice(BigDecimal.TEN);
        return products.save(product).getId();
    }
}
