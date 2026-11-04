package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.AlegraApiException;
import com.sciencebot.pos.billing.exception.BillingProviderException;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccess;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraClientHelper;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraCompany;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraCompanyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AlegraCompanyProviderTest {

    private static final String URL = "https://sandbox.example/e-provider/col/v1";
    private static final String COMPANY_ID = "01M6KH7R1QZQK687BNMX5BBXZC";
    private static final String TOKEN = "super-secret-token-123";
    private static final String COMPANY_JSON = "{\"company\":{\"id\":\"" + COMPANY_ID + "\",\"name\":\"Soluciones de Prueba SAS\","
            + "\"identification\":\"900559088\",\"dv\":\"2\",\"regimeCode\":\"R-99-PN\",\"organizationType\":1,"
            + "\"identificationType\":\"31\",\"email\":\"a@b.co\",\"governmentStatus\":{\"invoices\":\"AUTHORIZED\"},"
            + "\"someFutureField\":{\"x\":1}}}";

    private MockRestServiceServer server;
    private AlegraCompanyProvider provider;
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-04T12:00:00Z"));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        provider = new AlegraCompanyProvider(new AlegraClientHelper(builder.build()), Duration.ofMinutes(10), clock);
    }

    private static AlegraAccess access(Long storeId) {
        return new AlegraAccess(storeId, BillingEnvironment.SANDBOX, URL, TOKEN, COMPANY_ID);
    }

    @Test
    void fetch_CallsTheCompaniesEndpointWithTheBearerTokenAndParsesTheCompany() {
        server.expect(requestTo(URL + "/companies/" + COMPANY_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(COMPANY_JSON, MediaType.APPLICATION_JSON));

        AlegraCompany company = provider.get(access(5L));

        assertEquals(COMPANY_ID, company.id());
        assertEquals("Soluciones de Prueba SAS", company.name());
        assertEquals("900559088", company.identification());
        assertEquals("2", company.dv());
        assertEquals("AUTHORIZED", company.governmentStatus().get("invoices"));
        server.verify();
    }

    @Test
    void get_IsCachedPerStoreUntilInvalidatedOrExpired() {
        server.expect(times(3), requestTo(URL + "/companies/" + COMPANY_ID))
                .andRespond(withSuccess(COMPANY_JSON, MediaType.APPLICATION_JSON));

        provider.get(access(5L));
        provider.get(access(5L));
        provider.invalidate(5L);
        provider.get(access(5L));
        now.set(now.get().plus(Duration.ofMinutes(11)));
        provider.get(access(5L));

        server.verify();
    }

    @Test
    void cacheIsKeptApartPerStore() {
        server.expect(times(2), requestTo(URL + "/companies/" + COMPANY_ID))
                .andRespond(withSuccess(COMPANY_JSON, MediaType.APPLICATION_JSON));

        provider.get(access(5L));
        provider.get(access(6L));

        server.verify();
    }

    @Test
    void fetch_AlwaysCallsTheProviderEvenWithAFreshCache() {
        server.expect(times(2), requestTo(URL + "/companies/" + COMPANY_ID))
                .andRespond(withSuccess(COMPANY_JSON, MediaType.APPLICATION_JSON));

        provider.get(access(5L));
        provider.fetch(access(5L));

        server.verify();
    }

    @Test
    void invalidToken_401_FailsWithAClearMessageThatNeverLeaksTheToken() {
        server.expect(once(), requestTo(URL + "/companies/" + COMPANY_ID))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"AEP1001\",\"message\":\"Invalid token\"}]}"));

        AlegraApiException error = assertThrows(AlegraApiException.class, () -> provider.get(access(5L)));

        assertTrue(error.isAuthenticationError());
        assertEquals(401, error.getUpstreamStatus());
        assertFalse(error.getMessage().contains(TOKEN), error.getMessage());
        assertTrue(error.getMessage().contains("AEP1001"), "el mensaje de Alegra llega al usuario: " + error.getMessage());
        assertInstanceOf(BillingProviderException.class, error);
    }

    @Test
    void companyNotFound_404_IsReportedAsAProviderError() {
        server.expect(once(), requestTo(URL + "/companies/" + COMPANY_ID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"AEP1002\",\"message\":\"Company not found\"}]}"));

        AlegraApiException error = assertThrows(AlegraApiException.class, () -> provider.get(access(5L)));

        assertEquals(404, error.getUpstreamStatus());
        assertTrue(error.getMessage().contains("Company not found"));
    }

    @Test
    void connectionFailure_IsTemporaryAndCarriesNoStatus() {
        server.expect(once(), requestTo(URL + "/companies/" + COMPANY_ID))
                .andRespond(request -> { throw new IOException("connection reset"); });

        AlegraApiException error = assertThrows(AlegraApiException.class, () -> provider.get(access(5L)));

        assertEquals(0, error.getUpstreamStatus());
        assertTrue(error.isTemporary());
    }

    @Test
    void aConflictMessageNeverContainsTheLiteral409BecauseNoteServicesReadItAsAFactusConflict() {
        server.expect(once(), requestTo(URL + "/companies/" + COMPANY_ID))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"IDEMPOTENCY_REQUEST_IN_PROGRESS\",\"message\":\"in progress\"}]}"));

        AlegraApiException error = assertThrows(AlegraApiException.class, () -> provider.get(access(5L)));

        assertEquals(409, error.getUpstreamStatus());
        assertFalse(error.getMessage().contains("409"), error.getMessage());
    }
}
