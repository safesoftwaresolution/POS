package com.sciencebot.pos.voicesales;

import com.sciencebot.pos.config.security.JwtService;
import com.sciencebot.pos.config.security.PosUserDetails;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.products.internal.entities.Product;
import com.sciencebot.pos.products.internal.repositories.ProductRepository;
import com.sciencebot.pos.sales.SaleCompletedEvent;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import com.sciencebot.pos.voicesales.internal.ports.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"voice-sales.enabled=true", "voice-sales.transcription-provider=fixture",
        "voice-sales.extraction-provider=fixture", "spring.datasource.url=jdbc:h2:mem:voice_flow;MODE=PostgreSQL",
        "spring.jpa.show-sql=false"})
@RecordApplicationEvents
class VoiceCheckoutFlowTest {
    private static final String PATH = "/api/v1/voice-sales/preview";
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
    MockMvc mvc;

    @BeforeEach
    void setup() {
        // The JWT filter skips login when the thread already carries an authentication; start from a clean thread.
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        mvc =MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void cashierCorrectsQuantityAndConfirmsOnceWithoutElectronicEmission() throws Exception {
        String auth = cashier("caja-a", 41L, 41L);
        Long local = product(41L);
        Long foreign = product(42L);
        db.sql("create sequence if not exists invoice_seq start with 70000").update();
        when(transcriber.transcribe(any(), eq("audio/wav"))).thenReturn("Dos cafés grandes");
        when(extractor.extract("Dos cafés grandes")).thenReturn(new SaleIntent(
                List.of(new SaleIntent.Item("Café grande", 2)), null, null, null));

        long sales = count("sales"), moves = count("inventory_movements"), invoices = count("electronic_invoices");
        mvc.perform(post(PATH).header("Authorization", auth).contentType("audio/wav").content(WAV))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].item.status").value("RESOLVED"))
                .andExpect(jsonPath("$.items[0].item.candidates.length()").value(1))
                .andExpect(jsonPath("$.items[0].item.candidates[0].id").value(local))
                .andExpect(jsonPath("$.customer.status").value("NOT_PROVIDED"));
        assertThat(count("sales")).isEqualTo(sales);
        assertThat(count("inventory_movements")).isEqualTo(moves);
        assertThat(count("electronic_invoices")).isEqualTo(invoices);
        assertThat(products.findStockById(local)).isEqualTo(8);
        assertThat(events.stream(SaleCompletedEvent.class)).isEmpty();

        String key = UUID.randomUUID().toString();
        String corrected = """
                {"items":[{"productId":%d,"quantity":1}],"paymentMethod":"CASH",
                 "cashReceived":50000,"sendElectronicInvoice":false}
                """.formatted(local);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/sales").header("Authorization", auth).header("Idempotency-Key", key)
                    .contentType("application/json").content(corrected))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.items[0].quantity").value(1));
        }
        assertThat(count("sales")).isEqualTo(sales + 1);
        assertThat(count("inventory_movements")).isEqualTo(moves + 1);
        assertThat(products.findStockById(local)).isEqualTo(7);
        assertThat(products.findStockById(foreign)).isEqualTo(8);
        assertThat(events.stream(SaleCompletedEvent.class).toList()).singleElement()
                .satisfies(event -> assertThat(event.sendElectronicInvoice()).isFalse());
        assertThat(count("electronic_invoices")).isEqualTo(invoices);
    }

    @Test
    void staleTenantAndMalformedOrOversizedAudioNeverReachAI() throws Exception {
        mvc.perform(post(PATH).header("Authorization", cashier("moved", 42L, 41L))
                .contentType("audio/wav").content(WAV)).andExpect(status().isForbidden());
        String auth = cashier("seller", 42L, 42L);
        mvc.perform(post(PATH).header("Authorization", auth).contentType("audio/wav").content("not audio"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(PATH).header("Authorization", auth).contentType("application/json").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post(PATH).header("Authorization", auth).contentType("audio/webm")
                .content(new byte[5 * 1024 * 1024 + 1])).andExpect(status().is(413));
        verifyNoInteractions(transcriber, extractor);
    }

    @Test
    void providerErrorsArePrivateAndDoNotPublishSales() throws Exception {
        when(transcriber.transcribe(any(), anyString())).thenThrow(new IllegalStateException("sensitive fixture text"));
        var result = mvc.perform(post(PATH).header("Authorization", cashier("seller", 42L, 42L))
                        .contentType("audio/wav").content(WAV))
                .andExpect(status().isServiceUnavailable()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("sensitive");
        assertThat(events.stream(SaleCompletedEvent.class)).isEmpty();
        verifyNoInteractions(extractor);
    }

    private String cashier(String name, Long principalStore, Long jwtStore) {
        var principal = new PosUserDetails(name, "unused", true, List.of(new SimpleGrantedAuthority("ROLE_SELLER")),
                principalStore, "ACTIVE");
        when(identities.loadUserByUsername(name)).thenReturn(principal);
        var dto = new UserDto(103L, name, name, "fixture@example.invalid", "SELLER", true, principalStore, true);
        when(users.findByUsername(name)).thenReturn(Optional.of(dto));
        when(users.getById(103L)).thenReturn(dto);
        return "Bearer " + tokens.generateToken(principal, "SELLER", jwtStore);
    }

    private long count(String table) {
        return db.sql("select count(*) from " + table).query(Long.class).single();
    }

    private Long product(long store) {
        Product product = new Product();
        product.setName("Café grande");
        product.setInternalCode("CAFE");
        product.setStoreId(store);
        product.setCategoryId(1L);
        product.setQuantityAvailable(8);
        product.setSalePrice(BigDecimal.TEN);
        return products.save(product).getId();
    }
}
