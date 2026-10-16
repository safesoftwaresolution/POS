package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.exception.FactusApiException;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusAccess;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusClientHelper;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusCredentialsResolver;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusNumberingRangeClient;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusNumberingRangeMapper;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusTokenManager;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FactusNumberingRangeClientTest {

    private static final String URL = "https://sandbox.example";
    private static final String RANGES = URL + "/v2/numbering-ranges";
    private static final String RANGE_JSON = """
            {"id":14,"document":"21","document_name":"Factura de Venta","prefix":"SETP","from":1,"to":5000,
             "current":10,"resolution_number":"1876","start_date":"2026-01-01","end_date":"2027-01-01",
             "technical_key":"key","is_expired":0,"is_active":1}""";

    private MockRestServiceServer server;
    private FactusNumberingRangeClient rangeClient;
    private FactusCredentialsResolver resolver;

    @BeforeEach
    void setUp() {
        FactusTokenManager tokenManager = mock(FactusTokenManager.class);
        resolver = mock(FactusCredentialsResolver.class);
        FactusAccess access = new FactusAccess(5L, BillingEnvironment.SANDBOX, URL, "c", "s", "u", "p", 1);
        when(resolver.resolve(5L)).thenReturn(access);
        when(resolver.resolve(null)).thenReturn(access);
        when(tokenManager.getAccessToken(access)).thenReturn("tok-5");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        FactusClientHelper helper = new FactusClientHelper(tokenManager, resolver, builder.build());
        rangeClient = new FactusNumberingRangeClient(helper, new FactusNumberingRangeMapper());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getProviderName_ReturnsFactus() {
        assertEquals("factus", rangeClient.getProviderName());
    }

    @Test
    void numberingRanges_NullArguments_ThrowsException() {
        assertThrows(NullPointerException.class, () -> rangeClient.getNumberingRange(null));
        assertThrows(NullPointerException.class, () -> rangeClient.createNumberingRange(null));
        assertThrows(NullPointerException.class, () -> rangeClient.deleteNumberingRange(null));
        assertThrows(NullPointerException.class, () -> rangeClient.toggleNumberingRangeStatus(null));
        assertThrows(NullPointerException.class, () -> rangeClient.updateNumberingRangeCurrent(null, 1L));
        assertThrows(NullPointerException.class, () -> rangeClient.updateNumberingRangeCurrent(1L, null));
    }

    @Test
    void list_UsesTheTokenAndUrlOfTheCurrentStore() {
        TenantContext.setStoreId(5L);
        server.expect(requestTo(RANGES))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer tok-5"))
                .andRespond(withSuccess("{\"data\":[" + RANGE_JSON + "]}", MediaType.APPLICATION_JSON));

        List<NumberingRangeDto> ranges = rangeClient.listNumberingRanges();

        assertEquals(1, ranges.size());
        assertEquals("SETP", ranges.get(0).prefix());
        assertEquals("Factura de Venta", ranges.get(0).documentName());
        assertFalse(ranges.get(0).isExpired());
        assertTrue(ranges.get(0).isActive());
        server.verify();
    }

    @Test
    void list_RejectedCredentialsAreReportedInsteadOfReturningAnEmptyList() {
        server.expect(requestTo(RANGES))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"message\":\"Unauthenticated.\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        FactusApiException ex = assertThrows(FactusApiException.class, () -> rangeClient.listNumberingRanges());

        assertEquals(401, ex.getUpstreamStatus());
        assertTrue(ex.isAuthenticationError());
        assertTrue(ex.getMessage().contains("Unauthenticated"), "debe incluir el motivo que dio Factus");
    }

    @Test
    void list_UnreachableFactusIsATemporaryError() {
        server.expect(requestTo(RANGES)).andRespond(request -> { throw new IOException("timeout"); });

        FactusApiException ex = assertThrows(FactusApiException.class, () -> rangeClient.listNumberingRanges());

        assertTrue(ex.isTemporary());
    }

    @Test
    void storeThatCannotBillGetsNotEnabledInsteadOfUsingAnotherAccount() {
        when(resolver.resolve(null)).thenThrow(new BillingNotEnabledException("no activo"));

        assertThrows(BillingNotEnabledException.class, () -> rangeClient.listNumberingRanges());
        server.verify();
    }

    @Test
    void queryDian_ReadsAnObjectOrAList() {
        server.expect(requestTo(RANGES + "/dian"))
                .andRespond(withSuccess("{\"data\":{\"prefix\":\"SETP\",\"from\":1,\"to\":5000,\"resolution_number\":\"1876\"}}",
                        MediaType.APPLICATION_JSON));

        List<NumberingRangeDto> ranges = rangeClient.queryDianNumberingRanges();

        assertEquals(1, ranges.size());
        assertEquals(5000L, ranges.get(0).to());
    }

    @Test
    void queryDian_ErrorIsReported() {
        server.expect(requestTo(RANGES + "/dian")).andRespond(withServerError());

        FactusApiException ex = assertThrows(FactusApiException.class, () -> rangeClient.queryDianNumberingRanges());

        assertTrue(ex.isTemporary());
    }

    @Test
    void get_ReturnsTheRangeAndNullWhenItDoesNotExist() {
        server.expect(requestTo(RANGES + "/14"))
                .andRespond(withSuccess("{\"data\":" + RANGE_JSON + "}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(RANGES + "/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertEquals(14L, rangeClient.getNumberingRange(14L).id());
        assertNull(rangeClient.getNumberingRange(99L));
    }

    @Test
    void create_SendsOnlyTheFourFieldsFactusAccepts() {
        server.expect(requestTo(RANGES))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-5"))
                .andExpect(jsonPath("$.document").value("21"))
                .andExpect(jsonPath("$.prefix").value("SETP"))
                .andExpect(jsonPath("$.resolution_number").value("1876"))
                .andExpect(jsonPath("$.current").value("10"))
                .andExpect(jsonPath("$.from").doesNotExist())
                .andExpect(jsonPath("$.technical_key").doesNotExist())
                .andRespond(withSuccess("{\"data\":" + RANGE_JSON + "}", MediaType.APPLICATION_JSON));

        NumberingRangeDto created = rangeClient.createNumberingRange(
                new CreateNumberingRangeRequest("21", "SETP", "1876", 10L));

        assertEquals(14L, created.id());
        assertEquals("key", created.technicalKey());
        server.verify();
    }

    @Test
    void create_FactusValidationErrorKeepsItsMessage() {
        server.expect(requestTo(RANGES))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .body("{\"message\":\"La resolución no existe en la DIAN\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        FactusApiException ex = assertThrows(FactusApiException.class, () ->
                rangeClient.createNumberingRange(new CreateNumberingRangeRequest("21", "SETP", "0000", 1L)));

        assertEquals(422, ex.getUpstreamStatus());
        assertFalse(ex.isAuthenticationError());
        assertFalse(ex.isTemporary());
        assertTrue(ex.getMessage().contains("La resolución no existe en la DIAN"));
    }

    @Test
    void delete_SucceedsAndReportsErrors() {
        server.expect(requestTo(RANGES + "/14"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"status\":\"OK\",\"message\":\"Rango de numeración eliminado con éxito\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(RANGES + "/15")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertDoesNotThrow(() -> rangeClient.deleteNumberingRange(14L));
        assertThrows(FactusApiException.class, () -> rangeClient.deleteNumberingRange(15L));
    }

    @Test
    void toggle_ReturnsTheUpdatedRangeSoTheCallerKnowsTheNewState() {
        server.expect(requestTo(RANGES + "/14/toggle-status"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess("{\"data\":" + RANGE_JSON.replace("\"is_active\":1", "\"is_active\":0")
                        + "}", MediaType.APPLICATION_JSON));

        NumberingRangeDto toggled = rangeClient.toggleNumberingRangeStatus(14L);

        assertEquals(14L, toggled.id());
        assertFalse(toggled.isActive());
    }

    @Test
    void updateCurrent_SendsTheNumberAndReturnsTheUpdatedRange() {
        server.expect(requestTo(RANGES + "/14/current"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().string(containsString("\"current\":150")))
                .andRespond(withSuccess("{\"data\":" + RANGE_JSON.replace("\"current\":10", "\"current\":150")
                        + "}", MediaType.APPLICATION_JSON));

        NumberingRangeDto updated = rangeClient.updateNumberingRangeCurrent(14L, 150L);

        assertEquals(150L, updated.current());
        server.verify();
    }
}
