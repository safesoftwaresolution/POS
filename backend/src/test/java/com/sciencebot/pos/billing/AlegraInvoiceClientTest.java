package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccess;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccessResolver;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraClientHelper;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraCompany;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraCompanyProvider;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceClient;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceMapper;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator.Allocation;
import com.sciencebot.pos.billing.internal.adapters.dto.CustomerBillingData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Emision de una factura en Alegra: cada respuesta del proveedor se traduce al resultado y a la llave de idempotencia correctos. */
class AlegraInvoiceClientTest {

    private static final String URL = "https://sandbox.example/e-provider/col/v1";
    private static final String TOKEN = "platform-token";

    private AlegraAccessResolver resolver;
    private AlegraCompanyProvider companyProvider;
    private AlegraNumberAllocator allocator;
    private MockRestServiceServer server;
    private AlegraInvoiceClient client;

    @BeforeEach
    void setUp() {
        resolver = mock(AlegraAccessResolver.class);
        companyProvider = mock(AlegraCompanyProvider.class);
        allocator = mock(AlegraNumberAllocator.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AlegraInvoiceClient(resolver, companyProvider, allocator, new AlegraInvoiceMapper(),
                new AlegraClientHelper(builder.build()));

        AlegraAccess access = new AlegraAccess(5L, BillingEnvironment.SANDBOX, URL, TOKEN, "company-1");
        when(resolver.resolve(5L)).thenReturn(access);
        when(resolver.resolve(null)).thenReturn(access);
        when(companyProvider.get(access)).thenReturn(new AlegraCompany("company-1", "Empresa", null, "900559088", "2",
                "R-99-PN", 1, "31", "a@b.co", null, null, Map.of()));
        when(allocator.allocate(eq(5L), eq(BillingEnvironment.SANDBOX), eq("01"), anyString())).thenReturn(allocation(false, "key-1"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static Allocation allocation(boolean completed, String key) {
        return new Allocation(11L, 2L, "SETP", 990_000_101L, "18760000001", 990_000_000L, 995_000_000L,
                LocalDate.of(2019, 1, 19), LocalDate.of(2030, 1, 19), "technical-key", key, completed);
    }

    private static InvoiceRequest request() {
        CustomerBillingData customer = new CustomerBillingData("13", "222222222222", "", "2", "ZZ", List.of("R-99-PN"),
                "Consumidor Final", null, null, "c@c.co", null, null, "CO", "11001");
        InvoiceItemData item = new InvoiceItemData("REF-A", "Producto", new BigDecimal("1"), new BigDecimal("10000"),
                new BigDecimal("19"), new BigDecimal("10000"), "94", "999", "01", BigDecimal.ZERO, false);
        return new InvoiceRequest(7L, 5L, null, "FAC-1", "1", "10", customer, List.of(item), new BigDecimal("11900"), "obs");
    }

    private static String response(String status, String legalStatus, String code, String message, String... errorMessages) {
        String errors = String.join(",", java.util.Arrays.stream(errorMessages).map(m -> "\"" + m + "\"").toList());
        return "{\"invoice\":{\"id\":\"inv-123\",\"status\":\"" + status + "\"" + (legalStatus == null ? "" : ",\"legalStatus\":\"" + legalStatus + "\"")
                + ",\"cufe\":\"cufe-abc\",\"prefix\":\"SETP\",\"number\":990000101,\"fullNumber\":\"SETP990000101\","
                + "\"qrCodeContent\":\"qr-content\",\"governmentResponse\":{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"errorMessages\":[" + errors + "]},\"errors\":[]},\"files\":{\"xml\":\"https://x/xml\"}}";
    }

    private void expectPost(org.springframework.test.web.client.ResponseCreator creator) {
        server.expect(once(), requestTo(URL + "/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(header("Idempotency-Key", "key-1"))
                .andExpect(jsonPath("$.documentType").value("01"))
                .andExpect(jsonPath("$.number").value(990_000_101))
                .andExpect(jsonPath("$.company.id").value("company-1"))
                .andRespond(creator);
    }

    @Test
    void accepted_IsSuccessWithTheLegalNumberCufeAndProviderIdAndCompletesTheAllocation() {
        expectPost(withSuccess(response("SENT", "ACCEPTED", "00", "Procesado Correctamente."), MediaType.APPLICATION_JSON));

        InvoiceResult result = client.emitInvoice(request());

        assertTrue(result.isSuccess());
        assertEquals("VALIDATED", result.status());
        assertEquals("SETP990000101", result.legalInvoiceNumber());
        assertEquals("cufe-abc", result.cufe());
        assertEquals("qr-content", result.qrCode());
        assertNull(result.pdfUrl());
        assertEquals("inv-123", result.providerDocumentId());
        verify(allocator).markCompleted(11L);
        server.verify();
    }

    @Test
    void acceptedWithObservations_IsAlsoSuccess() {
        expectPost(withSuccess(response("SENT", "ACCEPTED_WITH_OBSERVATIONS", "00", "Procesado Correctamente.",
                "Regla: FAZ09, Notificación: x"), MediaType.APPLICATION_JSON));

        InvoiceResult result = client.emitInvoice(request());

        assertTrue(result.isSuccess());
        assertEquals("inv-123", result.providerDocumentId());
    }

    @Test
    void rejectedByTheDian_IsRejectedWithTheReasonsAndSoTheNextAttemptGetsANewKey() {
        expectPost(withSuccess(response("SENT", "REJECTED", "99", "Validación contiene errores",
                "Regla: 90, Rechazo: Documento procesado anteriormente."), MediaType.APPLICATION_JSON));

        InvoiceResult result = client.emitInvoice(request());

        assertEquals("REJECTED", result.status());
        assertTrue(result.errorMessage().contains("Regla: 90"), result.errorMessage());
        assertEquals("inv-123", result.providerDocumentId());
        verify(allocator).markCompleted(11L);
    }

    @Test
    void waitingForTheDian_IsPendingAndKeepsTheProviderIdWithoutCompletingTheAllocation() {
        expectPost(withSuccess(response("WAITING_RESPONSE", null, "", ""), MediaType.APPLICATION_JSON));

        InvoiceResult result = client.emitInvoice(request());

        assertEquals("PENDING", result.status());
        assertEquals("inv-123", result.providerDocumentId());
        assertTrue(result.errorMessage().contains("DIAN"));
        verify(allocator, never()).markCompleted(any());
    }

    @Test
    void failedStatus_IsAnErrorAndFreesTheKeyForTheNextAttempt() {
        expectPost(withSuccess(response("FAILED", null, "", "", "No se pudo firmar el documento"), MediaType.APPLICATION_JSON));

        InvoiceResult result = client.emitInvoice(request());

        assertEquals("ERROR", result.status());
        assertTrue(result.errorMessage().contains("No se pudo firmar"));
        verify(allocator).markCompleted(11L);
    }

    @Test
    void http400_IsRejectedBecauseTheDataMustBeCorrected() {
        expectPost(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"errors\":[{\"code\":\"AEP6012\",\"message\":\"The organizationType attribute is required\"}]}"));

        InvoiceResult result = client.emitInvoice(request());

        assertEquals("REJECTED", result.status());
        assertTrue(result.errorMessage().contains("AEP6012"), result.errorMessage());
        verify(allocator).markCompleted(11L);
    }

    @Test
    void credentialsServerErrorsAndTimeouts_ArePendingSoTheyAreRetriedWithTheSameKey() {
        for (var creator : List.of(
                withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("{\"errors\":[{\"code\":\"AEP1001\",\"message\":\"Invalid token\"}]}"),
                withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON).body("{}"),
                (org.springframework.test.web.client.ResponseCreator) request -> { throw new IOException("read timed out"); })) {
            server.reset();
            expectPost(creator);

            InvoiceResult result = client.emitInvoice(request());

            assertEquals("PENDING", result.status(), result.errorMessage());
            assertFalse(result.errorMessage().contains(TOKEN), "el token nunca llega al mensaje");
        }
        verify(allocator, never()).markCompleted(any());
        verify(allocator, never()).rotateIdempotencyKey(any());
    }

    @Test
    void conflictWhileInProgress_IsPendingWithoutTheLiteral409() {
        expectPost(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body("{\"errors\":[{\"code\":\"IDEMPOTENCY_REQUEST_IN_PROGRESS\",\"message\":\"in progress\"}]}"));

        InvoiceResult result = client.emitInvoice(request());

        assertEquals("PENDING", result.status());
        assertFalse(result.errorMessage().contains("409"), result.errorMessage());
    }

    @Test
    void keyReusedWithADifferentPayload_422_RotatesTheKeyAndReportsPending() {
        expectPost(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                .body("{\"errors\":[{\"code\":\"IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD\",\"message\":\"reused\"}]}"));
        when(allocator.rotateIdempotencyKey(11L)).thenReturn(allocation(false, "key-2"));

        InvoiceResult result = client.emitInvoice(request());

        assertEquals("PENDING", result.status());
        verify(allocator).rotateIdempotencyKey(11L);
    }

    @Test
    void otherClientErrors_AreErrors() {
        expectPost(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                .body("{\"errors\":[{\"code\":\"AEP1002\",\"message\":\"Company not found\"}]}"));

        InvoiceResult result = client.emitInvoice(request());

        assertEquals("ERROR", result.status());
        assertTrue(result.errorMessage().contains("Company not found"));
    }

    @Test
    void afterAFinalNonAcceptedOutcomeTheNumberIsKeptButTheKeyIsRotatedBeforeSending() {
        when(allocator.allocate(eq(5L), eq(BillingEnvironment.SANDBOX), eq("01"), anyString())).thenReturn(allocation(true, "old-key"));
        when(allocator.rotateIdempotencyKey(11L)).thenReturn(allocation(false, "key-1"));
        expectPost(withSuccess(response("SENT", "ACCEPTED", "00", "ok"), MediaType.APPLICATION_JSON));

        InvoiceResult result = client.emitInvoice(request());

        assertTrue(result.isSuccess());
        verify(allocator).rotateIdempotencyKey(11L);
        server.verify();
    }

    @Test
    void theReferenceIsDeterministicPerSoSaleSoARetryReusesTheSameNumber() {
        server.expect(once(), requestTo(URL + "/invoices")).andRespond(withSuccess(response("SENT", "ACCEPTED", "00", "ok"), MediaType.APPLICATION_JSON));

        client.emitInvoice(request());

        verify(allocator).allocate(5L, BillingEnvironment.SANDBOX, "01", "FAC-1-7");
    }

    @Test
    void aRangeWithoutDatesFailsAsAnErrorWithoutCallingAlegra() {
        when(allocator.allocate(eq(5L), eq(BillingEnvironment.SANDBOX), eq("01"), anyString()))
                .thenReturn(new Allocation(11L, 2L, "SETP", 1L, "1876", 1L, 10L, null, null, null, "key-1", false));

        InvoiceResult result = client.emitInvoice(request());

        assertEquals("ERROR", result.status());
        assertTrue(result.errorMessage().contains("vigencia"), result.errorMessage());
        server.verify();
    }

    @Test
    void aStoreWithoutAccessPropagatesTheNotEnabledError() {
        when(resolver.resolve(5L)).thenThrow(new BillingNotEnabledException("Falta el company.id"));

        assertThrows(BillingNotEnabledException.class, () -> client.emitInvoice(request()));
        verifyNoInteractions(allocator);
    }

    @Test
    void queryByProviderId_ReadsTheDocumentAndMapsItLikeAnEmission() {
        TenantContext.setStoreId(5L);
        server.expect(once(), requestTo(URL + "/invoices/inv-123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(response("SENT", "ACCEPTED", "00", "ok"), MediaType.APPLICATION_JSON));

        InvoiceResult result = client.queryInvoiceByProviderId("inv-123");

        assertTrue(result.isSuccess());
        assertEquals("SETP990000101", result.legalInvoiceNumber());
        assertEquals("inv-123", result.providerDocumentId());
    }

    @Test
    void queryByProviderId_StillWaitingStaysPendingAndNotFoundIsAnError() {
        TenantContext.setStoreId(5L);
        server.expect(once(), requestTo(URL + "/invoices/inv-123"))
                .andRespond(withSuccess(response("WAITING_RESPONSE", null, "", ""), MediaType.APPLICATION_JSON));
        assertEquals("PENDING", client.queryInvoiceByProviderId("inv-123").status());

        server.reset();
        server.expect(once(), requestTo(URL + "/invoices/inv-404"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"AEP2001\",\"message\":\"Document not found\"}]}"));
        assertEquals("ERROR", client.queryInvoiceByProviderId("inv-404").status());
    }
}
