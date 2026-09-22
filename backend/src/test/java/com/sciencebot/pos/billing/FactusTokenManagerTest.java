package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.internal.adapters.factus.FactusAccess;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusCredentialsResolver;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusTokenManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FactusTokenManagerTest {

    private static final String URL = "https://sandbox.example";

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private FactusCredentialsResolver resolver;
    private MockRestServiceServer server;
    private FactusTokenManager manager;

    @BeforeEach
    void setUp() {
        resolver = mock(FactusCredentialsResolver.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        manager = new FactusTokenManager(builder.build(), resolver, now::get);
    }

    private static FactusAccess access(Long storeId, BillingEnvironment env, String clientId, String password) {
        return new FactusAccess(storeId, env, URL, clientId, "secret-" + clientId, "user-" + clientId, password, 1);
    }

    private static String tokenJson(String token, int expiresIn) {
        return "{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\",\"expires_in\":" + expiresIn + "}";
    }

    @Test
    void requestsTokenWithTheCredentialsOfThatStore() {
        when(resolver.resolve(5L)).thenReturn(access(5L, BillingEnvironment.SANDBOX, "cli5", "pass5"));
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=cli5")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=secret-cli5")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("username=user-cli5")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("password=pass5")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=password")))
                .andRespond(withSuccess(tokenJson("tok-5", 3600), MediaType.APPLICATION_JSON));

        assertEquals("tok-5", manager.getAccessToken(5L));
        server.verify();
    }

    @Test
    void reusesTheTokenWhileItIsValid() {
        FactusAccess access = access(5L, BillingEnvironment.SANDBOX, "cli5", "pass5");
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andRespond(withSuccess(tokenJson("tok-5", 3600), MediaType.APPLICATION_JSON));

        assertEquals("tok-5", manager.getAccessToken(access));
        now.addAndGet(3_000_000L); // 50 minutos: dentro del margen de una hora
        assertEquals("tok-5", manager.getAccessToken(access));
        server.verify();
    }

    @Test
    void asksForANewTokenWhenTheOldOneIsAboutToExpire() {
        FactusAccess access = access(5L, BillingEnvironment.SANDBOX, "cli5", "pass5");
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andRespond(withSuccess(tokenJson("tok-old", 3600), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andRespond(withSuccess(tokenJson("tok-new", 3600), MediaType.APPLICATION_JSON));

        assertEquals("tok-old", manager.getAccessToken(access));
        now.addAndGet(3_541_000L); // pasa de 3600 s - 60 s de margen
        assertEquals("tok-new", manager.getAccessToken(access));
        server.verify();
    }

    @Test
    void tokensOfDifferentStoresAreNeverMixed() {
        FactusAccess storeA = access(5L, BillingEnvironment.SANDBOX, "cliA", "passA");
        FactusAccess storeB = access(6L, BillingEnvironment.SANDBOX, "cliB", "passB");
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=cliA")))
                .andRespond(withSuccess(tokenJson("tok-A", 3600), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=cliB")))
                .andRespond(withSuccess(tokenJson("tok-B", 3600), MediaType.APPLICATION_JSON));

        assertEquals("tok-A", manager.getAccessToken(storeA));
        assertEquals("tok-B", manager.getAccessToken(storeB));
        assertEquals("tok-A", manager.getAccessToken(storeA));
        server.verify();
    }

    @Test
    void sameStoreKeepsSeparateTokensPerEnvironment() {
        FactusAccess sandbox = access(5L, BillingEnvironment.SANDBOX, "cliS", "passS");
        FactusAccess production = access(5L, BillingEnvironment.PRODUCTION, "cliP", "passP");
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andRespond(withSuccess(tokenJson("tok-sandbox", 3600), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andRespond(withSuccess(tokenJson("tok-prod", 3600), MediaType.APPLICATION_JSON));

        assertEquals("tok-sandbox", manager.getAccessToken(sandbox));
        assertEquals("tok-prod", manager.getAccessToken(production));
        assertEquals("tok-sandbox", manager.getAccessToken(sandbox));
        server.verify();
    }

    @Test
    void changingTheCredentialsDiscardsTheStoredToken() {
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andRespond(withSuccess(tokenJson("tok-old-creds", 3600), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("password=newpass")))
                .andRespond(withSuccess(tokenJson("tok-new-creds", 3600), MediaType.APPLICATION_JSON));

        assertEquals("tok-old-creds", manager.getAccessToken(access(5L, BillingEnvironment.SANDBOX, "cli5", "oldpass")));
        assertEquals("tok-new-creds", manager.getAccessToken(access(5L, BillingEnvironment.SANDBOX, "cli5", "newpass")));
        server.verify();
    }

    @Test
    void invalidateForcesANewTokenForBothEnvironments() {
        FactusAccess access = access(5L, BillingEnvironment.SANDBOX, "cli5", "pass5");
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andRespond(withSuccess(tokenJson("tok-1", 3600), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andRespond(withSuccess(tokenJson("tok-2", 3600), MediaType.APPLICATION_JSON));

        assertEquals("tok-1", manager.getAccessToken(access));
        manager.invalidate(5L);
        assertEquals("tok-2", manager.getAccessToken(access));
        server.verify();
    }

    @Test
    void rejectedCredentialsBecomeAnErrorThatSaysWhatHappened() {
        when(resolver.resolve(5L)).thenReturn(access(5L, BillingEnvironment.PRODUCTION, "cli5", "bad"));
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":\"invalid_grant\"}").contentType(MediaType.APPLICATION_JSON));

        FactusApiException ex = assertThrows(FactusApiException.class, () -> manager.getAccessToken(5L));

        assertEquals(401, ex.getUpstreamStatus());
        assertTrue(ex.isAuthenticationError());
        assertTrue(ex.getMessage().contains("PRODUCTION"));
        assertFalse(ex.getMessage().contains("bad"), "el mensaje no debe filtrar la contraseña");
    }

    @Test
    void unreachableFactusIsATemporaryError() {
        FactusAccess access = access(5L, BillingEnvironment.SANDBOX, "cli5", "pass5");
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andRespond(request -> { throw new IOException("connection reset"); });

        FactusApiException ex = assertThrows(FactusApiException.class, () -> manager.getAccessToken(access));

        assertEquals(0, ex.getUpstreamStatus());
        assertTrue(ex.isTemporary());
    }

    @Test
    void factusAnsweringWithoutTokenIsAnError() {
        FactusAccess access = access(5L, BillingEnvironment.SANDBOX, "cli5", "pass5");
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(FactusApiException.class, () -> manager.getAccessToken(access));
    }

    @Test
    void storeThatCannotBillGetsNoTokenAndNoRequestIsMade() {
        when(resolver.resolve(5L)).thenThrow(new BillingNotEnabledException("no activo"));

        assertThrows(BillingNotEnabledException.class, () -> manager.getAccessToken(5L));
        server.verify(); // sin expectativas: fallaría si se hubiera llamado a Factus
    }

    @Test
    void verifyConnectionWorksBeforeActivationAndRefreshesTheStoredToken() {
        when(resolver.resolveIgnoringStatus(5L)).thenReturn(access(5L, BillingEnvironment.SANDBOX, "cli5", "pass5"));
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andRespond(withSuccess(tokenJson("tok-test", 3600), MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> manager.verifyConnection(5L));

        // El token de la prueba queda guardado: la primera factura no pide otro.
        assertEquals("tok-test", manager.getAccessToken(access(5L, BillingEnvironment.SANDBOX, "cli5", "pass5")));
        server.verify();
    }

    @Test
    void verifyConnectionReportsRejectedCredentials() {
        when(resolver.resolveIgnoringStatus(5L)).thenReturn(access(5L, BillingEnvironment.SANDBOX, "cli5", "bad"));
        server.expect(once(), requestTo(URL + "/oauth/token"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

        assertThrows(FactusApiException.class, () -> manager.verifyConnection(5L));
    }
}
