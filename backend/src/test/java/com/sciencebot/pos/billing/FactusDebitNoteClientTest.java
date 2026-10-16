package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.adapters.dto.DebitNoteResult;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusAccess;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusClientHelper;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusCredentialsResolver;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusDebitNoteClient;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusTokenManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FactusDebitNoteClientTest {

    private static final String URL = "https://sandbox.example";

    private FactusTokenManager tokenManager;
    private FactusCredentialsResolver resolver;
    private MockRestServiceServer server;
    private FactusDebitNoteClient debitNoteClient;

    @BeforeEach
    void setUp() {
        tokenManager = mock(FactusTokenManager.class);
        resolver = mock(FactusCredentialsResolver.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        FactusClientHelper helper = new FactusClientHelper(tokenManager, resolver, builder.build());
        debitNoteClient = new FactusDebitNoteClient(helper);
    }

    private void setupAccess() {
        FactusAccess access = new FactusAccess(5L, BillingEnvironment.SANDBOX, URL, "client", "secret", "user", "pass", 1776);
        when(resolver.resolve(5L)).thenReturn(access);
        when(tokenManager.getAccessToken(access)).thenReturn("tok-5");
    }

    private CreateDebitNoteRequest createSampleRequest() {
        return new CreateDebitNoteRequest(
                "ND-2026-0001",
                "1",
                "30",
                "SETP990002519",
                1776,
                "Intereses por mora",
                List.of(
                        new CreateDebitNoteRequest.PaymentDetailRequest("1", "10", "pago-nd-001", null, "10000.00"),
                        new CreateDebitNoteRequest.PaymentDetailRequest("2", "10", "pago-nd-002", "2026-11-30", "7850.00")
                ),
                new CreateDebitNoteRequest.CustomerDebitNoteRequest(
                        "31", "123.456.789", "4", "Alan company", "Alan trade", null, "calle 1", "alan@email.com", "123456", "1", "ZZ", "CO", List.of("R-99-PN"), "68679"
                ),
                List.of(new CreateDebitNoteRequest.ItemDebitNoteRequest(
                        "INT-MORA", "Intereses por mora", "1.00", "0.00", "15000.00", "94", "999",
                        List.of(new CreateDebitNoteRequest.ItemDebitNoteRequest.TaxItemRequest("01", "19.00"))
                ))
        );
    }

    @Test
    void getProviderName_ReturnsFactus() {
        assertEquals("factus", debitNoteClient.getProviderName());
    }

    @Test
    void emitDebitNote_BlankReferenceCode_ReturnsErrorWithoutCallingFactus() {
        CreateDebitNoteRequest invalid = new CreateDebitNoteRequest(" ", "1", "30", "SETP-01", 1776, null, List.of(), null, List.of());

        DebitNoteResult result = debitNoteClient.emitDebitNote(invalid, 5L);

        assertFalse(result.isSuccess());
        assertEquals("ERROR", result.status());
        assertTrue(result.errorMessage().contains("reference_code es obligatorio"));
        verifyNoInteractions(resolver);
    }

    @Test
    void emitDebitNote_BillingNotEnabled_ReturnsError() {
        when(resolver.resolve(5L)).thenThrow(new BillingNotEnabledException("La facturación electrónica no está activa para el local"));

        DebitNoteResult result = debitNoteClient.emitDebitNote(createSampleRequest(), 5L);

        assertEquals("ERROR", result.status());
        assertTrue(result.errorMessage().contains("no está activa"));
    }

    @Test
    void emitDebitNote_SuccessfulValidation_PostsExpectedPayloadAndReturnsValidatedResult() {
        setupAccess();

        String responseJson = """
                {
                  "status": "Created",
                  "message": "Nota débito creada y validada exitosamente",
                  "data": {
                    "debit_note": {
                      "number": "ND990000001",
                      "cude": "cude-hash-12345",
                      "is_validated": true
                    },
                    "links": {
                      "qr": "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=cude-hash-12345",
                      "public_url": "https://api-sandbox.factus.com.co/v2/debit-notes/ND990000001/public"
                    }
                  }
                }
                """;

        server.expect(requestTo(URL + "/v2/debit-notes/validate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-5"))
                .andExpect(jsonPath("$.reference_code").value("ND-2026-0001"))
                .andExpect(jsonPath("$.correction_concept_code").value("1"))
                .andExpect(jsonPath("$.customization_id").value("30"))
                .andExpect(jsonPath("$.bill_number").value("SETP990002519"))
                .andExpect(jsonPath("$.numbering_range_id").value(1776))
                .andExpect(jsonPath("$.payment_details[0].due_date").doesNotExist())
                .andExpect(jsonPath("$.payment_details[1].due_date").value("2026-11-30"))
                .andExpect(jsonPath("$.customer.identification").value("123456789"))
                .andExpect(jsonPath("$.customer.company").value("Alan company"))
                .andExpect(jsonPath("$.items[0].code_reference").value("INT-MORA"))
                .andExpect(jsonPath("$.items[0].taxes[0].rate").value("19.00"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        DebitNoteResult result = debitNoteClient.emitDebitNote(createSampleRequest(), 5L);

        assertTrue(result.isSuccess());
        assertEquals("VALIDATED", result.status());
        assertEquals("ND990000001", result.debitNoteNumber());
        assertEquals("cude-hash-12345", result.cude());
        assertTrue(result.qrCode().contains("cude-hash-12345"));
        assertTrue(result.pdfUrl().endsWith("/public"));
        server.verify();
    }

    @Test
    void emitDebitNote_FlatDataResponse_ReadsNumberFromData() {
        setupAccess();

        server.expect(requestTo(URL + "/v2/debit-notes/validate"))
                .andRespond(withSuccess("{\"data\":{\"number\":\"ND990000005\",\"cude\":\"cude-5\",\"qr\":\"qr-5\"}}", MediaType.APPLICATION_JSON));

        DebitNoteResult result = debitNoteClient.emitDebitNote(createSampleRequest(), 5L);

        assertTrue(result.isSuccess());
        assertEquals("ND990000005", result.debitNoteNumber());
        assertEquals("qr-5", result.qrCode());
        server.verify();
    }

    @Test
    void emitDebitNote_ResponseWithoutData_ReturnsError() {
        setupAccess();

        server.expect(requestTo(URL + "/v2/debit-notes/validate"))
                .andRespond(withSuccess("{\"status\":\"OK\"}", MediaType.APPLICATION_JSON));

        DebitNoteResult result = debitNoteClient.emitDebitNote(createSampleRequest(), 5L);

        assertEquals("ERROR", result.status());
        server.verify();
    }

    @Test
    void emitDebitNote_Conflict409_ReturnsErrorWithConflictMessage() {
        setupAccess();

        server.expect(requestTo(URL + "/v2/debit-notes/validate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("{\"message\":\"Se encontró un documento pendiente por enviar a la DIAN. Error 409 Conflict\"}"));

        DebitNoteResult result = debitNoteClient.emitDebitNote(createSampleRequest(), 5L);

        assertFalse(result.isSuccess());
        assertEquals("ERROR", result.status());
        assertTrue(result.errorMessage().contains("409"));
        server.verify();
    }

    @Test
    void emitDebitNote_ValidationError422_ReturnsRejected() {
        setupAccess();

        server.expect(requestTo(URL + "/v2/debit-notes/validate"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .body("{\"message\":\"El campo payment_details.1.due_date es obligatorio\"}"));

        DebitNoteResult result = debitNoteClient.emitDebitNote(createSampleRequest(), 5L);

        assertEquals("REJECTED", result.status());
        assertTrue(result.errorMessage().contains("422"));
        assertTrue(result.errorMessage().contains("due_date"));
        server.verify();
    }

    @Test
    void emitDebitNote_Unauthorized401_InvalidatesTokenAndReturnsPending() {
        setupAccess();

        server.expect(requestTo(URL + "/v2/debit-notes/validate"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        DebitNoteResult result = debitNoteClient.emitDebitNote(createSampleRequest(), 5L);

        assertEquals("PENDING", result.status());
        verify(tokenManager).invalidate(5L);
        server.verify();
    }

    @Test
    void emitDebitNote_ServerError500_ReturnsPending() {
        setupAccess();

        server.expect(requestTo(URL + "/v2/debit-notes/validate"))
                .andRespond(withServerError());

        DebitNoteResult result = debitNoteClient.emitDebitNote(createSampleRequest(), 5L);

        assertEquals("PENDING", result.status());
        assertTrue(result.errorMessage().contains("Factus no disponible"));
        server.verify();
    }

    @Test
    void toFactusDebitNotePayload_MinimalRequest_AppliesDefaultsAndOmitsOptionalFields() {
        CreateDebitNoteRequest minimal = new CreateDebitNoteRequest(
                "ND-MIN", "4", null, "SETP-01", null, null, null, null,
                List.of(new CreateDebitNoteRequest.ItemDebitNoteRequest(null, null, null, null, null, null, null, null))
        );

        Map<String, Object> payload = debitNoteClient.toFactusDebitNotePayload(minimal);

        assertEquals("30", payload.get("customization_id"));
        assertFalse(payload.containsKey("numbering_range_id"));
        assertFalse(payload.containsKey("customer"));
        assertFalse(payload.containsKey("payment_details"));
        @SuppressWarnings("unchecked")
        Map<String, Object> item = ((List<Map<String, Object>>) payload.get("items")).get(0);
        assertEquals("94", item.get("unit_measure_code"));
        assertEquals(List.of(), item.get("taxes"));
    }

    @Test
    void deleteUnvalidatedDebitNote_Success_ReturnsTrue() {
        setupAccess();

        server.expect(requestTo(URL + "/v2/debit-notes/reference/ND-2026-0001"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer tok-5"))
                .andRespond(withNoContent());

        assertTrue(debitNoteClient.deleteUnvalidatedDebitNote("ND-2026-0001", 5L));
        server.verify();
    }

    @Test
    void deleteUnvalidatedDebitNote_FactusError_ReturnsFalse() {
        setupAccess();

        server.expect(requestTo(URL + "/v2/debit-notes/reference/ND-2026-0001"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT));

        assertFalse(debitNoteClient.deleteUnvalidatedDebitNote("ND-2026-0001", 5L));
        server.verify();
    }

    @Test
    void downloadDebitNotePDF_DecodesBase64Successfully() {
        setupAccess();

        String rawContent = "MOCK_PDF_CONTENT";
        String encoded = Base64.getEncoder().encodeToString(rawContent.getBytes());
        String responseJson = "{\"data\":{\"file_name\":\"ND990000001.pdf\",\"pdf_base_64_encoded\":\"" + encoded + "\"}}";

        server.expect(requestTo(URL + "/v2/debit-notes/ND990000001/download-pdf"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer tok-5"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        byte[] pdf = debitNoteClient.downloadDebitNotePDF("ND990000001", 5L);

        assertEquals(rawContent, new String(pdf));
        server.verify();
    }

    @Test
    void downloadDebitNoteXML_DecodesBase64Successfully() {
        setupAccess();

        String rawContent = "<DebitNote>Legal</DebitNote>";
        String encoded = Base64.getEncoder().encodeToString(rawContent.getBytes());
        String responseJson = "{\"data\":{\"file_name\":\"ND990000001.xml\",\"xml_base_64_encoded\":\"" + encoded + "\"}}";

        server.expect(requestTo(URL + "/v2/debit-notes/ND990000001/download-xml"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer tok-5"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        byte[] xml = debitNoteClient.downloadDebitNoteXML("ND990000001", 5L);

        assertEquals(rawContent, new String(xml));
        server.verify();
    }

    @Test
    void downloadDebitNotePDF_FactusError_ReturnsNull() {
        setupAccess();

        server.expect(requestTo(URL + "/v2/debit-notes/ND990000001/download-pdf"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertNull(debitNoteClient.downloadDebitNotePDF("ND990000001", 5L));
        server.verify();
    }
}
