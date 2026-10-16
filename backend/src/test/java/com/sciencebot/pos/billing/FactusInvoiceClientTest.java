package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.exception.FactusApiException;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusAccess;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusClientHelper;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusCredentialsResolver;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusInvoiceClient;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusSaleMapper;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusTokenManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FactusInvoiceClientTest {

    private static final String URL = "https://sandbox.example";

    private FactusTokenManager tokenManager;
    private FactusCredentialsResolver resolver;
    private MockRestServiceServer server;
    private FactusInvoiceClient invoiceClient;

    @BeforeEach
    void setUp() {
        tokenManager = mock(FactusTokenManager.class);
        resolver = mock(FactusCredentialsResolver.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        FactusClientHelper helper = new FactusClientHelper(tokenManager, resolver, builder.build());
        invoiceClient = new FactusInvoiceClient(helper, new FactusSaleMapper());
    }

    private FactusAccess accessWithRange(Integer rangeId) {
        FactusAccess access = new FactusAccess(5L, BillingEnvironment.SANDBOX, URL, "c", "s", "u", "p", rangeId);
        when(resolver.resolve(5L)).thenReturn(access);
        when(tokenManager.getAccessToken(access)).thenReturn("tok-5");
        return access;
    }

    private static InvoiceRequest request(Integer explicitRange) {
        return new InvoiceRequest(1L, 5L, explicitRange, "FACT-001", "1", "10", null, List.of(), BigDecimal.TEN, null);
    }

    @Test
    void getProviderName_ReturnsFactus() {
        assertEquals("factus", invoiceClient.getProviderName());
    }

    @Test
    void emitInvoice_NullSaleId_ReturnsErrorResult() {
        InvoiceRequest request = new InvoiceRequest(null, "FACT-001", "10", null, List.of(), BigDecimal.TEN);
        InvoiceResult result = invoiceClient.emitInvoice(request);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("ERROR", result.status());
        assertTrue(result.errorMessage().contains("ID de la venta es obligatorio"));
    }

    @Test
    void queryInvoice_ReturnsPending() {
        InvoiceResult result = invoiceClient.queryInvoice("FACT-001");
        assertNotNull(result);
        assertEquals("PENDING", result.status());
        assertTrue(result.errorMessage().contains("FACT-001"));
    }

    @Test
    void sendInvoiceEmail_NullArguments_ThrowsException() {
        assertThrows(NullPointerException.class, () -> invoiceClient.sendInvoiceEmail(null, "correo@tienda.com"));
        assertThrows(NullPointerException.class, () -> invoiceClient.sendInvoiceEmail("FACT-001", null));
    }

    @Test
    void emitInvoice_UsesTheRangeConfiguredForTheStore() {
        accessWithRange(77);
        server.expect(requestTo(URL + "/v2/bills/validate"))
                .andExpect(header("Authorization", "Bearer tok-5"))
                .andExpect(jsonPath("$.numbering_range_id").value(77))
                .andRespond(withSuccess("{\"data\":{\"bill\":{\"number\":\"SETP1\",\"cufe\":\"abc\"},"
                        + "\"links\":{\"qr\":\"qr\",\"public_url\":\"pub\"}}}", MediaType.APPLICATION_JSON));

        InvoiceResult result = invoiceClient.emitInvoice(request(null));

        assertTrue(result.isSuccess());
        assertEquals("SETP1", result.legalInvoiceNumber());
        server.verify();
    }

    @Test
    void emitInvoice_SendsTheSameReferenceCodeOnEveryAttempt() {
        accessWithRange(77);
        server.expect(requestTo(URL + "/v2/bills/validate"))
                .andExpect(jsonPath("$.reference_code").value("FACT-001-1"))
                .andRespond(withServerError());
        server.expect(requestTo(URL + "/v2/bills/validate"))
                .andExpect(jsonPath("$.reference_code").value("FACT-001-1"))
                .andRespond(withServerError());

        assertEquals("PENDING", invoiceClient.emitInvoice(request(null)).status());
        assertEquals("PENDING", invoiceClient.emitInvoice(request(null)).status());
        server.verify();
    }

    @Test
    void emitInvoice_On409DeletesTheUnvalidatedDraftAndRetriesOnce() {
        accessWithRange(77);
        server.expect(requestTo(URL + "/v2/bills/validate")).andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(requestTo(URL + "/v1/bills/destroy/reference/FACT-001-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());
        server.expect(requestTo(URL + "/v2/bills/validate"))
                .andRespond(withSuccess("{\"data\":{\"bill\":{\"number\":\"SETP2\",\"cufe\":\"abc\"}}}", MediaType.APPLICATION_JSON));

        InvoiceResult result = invoiceClient.emitInvoice(request(null));

        assertTrue(result.isSuccess());
        assertEquals("SETP2", result.legalInvoiceNumber());
        server.verify();
    }

    @Test
    void emitInvoice_On409WhenTheDraftCannotBeDeletedIsRejectedNotRetried() {
        accessWithRange(77);
        server.expect(requestTo(URL + "/v2/bills/validate")).andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(requestTo(URL + "/v1/bills/destroy/reference/FACT-001-1"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        InvoiceResult result = invoiceClient.emitInvoice(request(null));

        assertEquals("REJECTED", result.status());
        server.verify();
    }

    @Test
    void emitInvoice_WithoutRangeForTheStoreDoesNotCallFactus() {
        accessWithRange(null);

        InvoiceResult result = invoiceClient.emitInvoice(request(null));

        assertEquals("ERROR", result.status());
        assertTrue(result.errorMessage().contains("rango de numeración"));
        server.verify(); // no hubo llamada a Factus
    }

    @Test
    void emitInvoice_StoreNotEnabledReturnsErrorWithTheReason() {
        when(resolver.resolve(5L)).thenThrow(new BillingNotEnabledException("La facturación electrónica del local no está activa"));

        InvoiceResult result = invoiceClient.emitInvoice(request(null));

        assertEquals("ERROR", result.status());
        assertEquals("La facturación electrónica del local no está activa", result.errorMessage());
        server.verify();
    }

    @Test
    void emitInvoice_RejectedCredentialsLeaveTheInvoicePendingSoItCanBeRetried() {
        FactusAccess access = accessWithRange(77);
        when(tokenManager.getAccessToken(access))
                .thenThrow(new FactusApiException(401, "Factus no aceptó las credenciales del local", null));

        InvoiceResult result = invoiceClient.emitInvoice(request(null));

        assertEquals("PENDING", result.status());
        assertTrue(result.errorMessage().contains("credenciales"));
    }

    @Test
    void emitInvoice_Unauthorized401DiscardsTheStoredToken() {
        accessWithRange(77);
        server.expect(requestTo(URL + "/v2/bills/validate")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        InvoiceResult result = invoiceClient.emitInvoice(request(null));

        assertEquals("PENDING", result.status());
        verify(tokenManager).invalidate(5L);
    }
}
