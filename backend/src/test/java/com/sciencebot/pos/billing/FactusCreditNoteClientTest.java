package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusAccess;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusClientHelper;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusCredentialsResolver;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusCreditNoteClient;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FactusCreditNoteClientTest {

    private static final String URL = "https://sandbox.example";

    private FactusTokenManager tokenManager;
    private FactusCredentialsResolver resolver;
    private MockRestServiceServer server;
    private FactusCreditNoteClient creditNoteClient;

    @BeforeEach
    void setUp() {
        tokenManager = mock(FactusTokenManager.class);
        resolver = mock(FactusCredentialsResolver.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        FactusClientHelper helper = new FactusClientHelper(tokenManager, resolver, builder.build());
        creditNoteClient = new FactusCreditNoteClient(helper);
    }

    private FactusAccess setupAccess() {
        FactusAccess access = new FactusAccess(5L, BillingEnvironment.SANDBOX, URL, "client", "secret", "user", "pass", 1776);
        when(resolver.resolve(5L)).thenReturn(access);
        when(tokenManager.getAccessToken(access)).thenReturn("tok-5");
        return access;
    }

    private CreateCreditNoteRequest createSampleRequest() {
        return new CreateCreditNoteRequest(
                "NOTA-2026-0015",
                "2",
                "20",
                "SETP990002519",
                1776,
                "Anulación nota crédito",
                List.of(new CreateCreditNoteRequest.PaymentDetailRequest("1", "42", "pago-001", "83300.00")),
                new CreateCreditNoteRequest.CustomerCreditNoteRequest(
                        "31", "123456789", "4", "Alan company", "Alan trade", null, "calle 1", "alan@email.com", "123456", "1", "ZZ", "CO", List.of("R-99-PN"), "68679"
                ),
                List.of(new CreateCreditNoteRequest.ItemCreditNoteRequest(
                        "PROD-000A", "Producto A", "1.00", "0.00", "10000.00", "94", "999",
                        List.of(new CreateCreditNoteRequest.ItemCreditNoteRequest.TaxItemRequest("01", "19.00"))
                ))
        );
    }

    @Test
    void getProviderName_ReturnsFactus() {
        assertEquals("factus", creditNoteClient.getProviderName());
    }

    @Test
    void emitCreditNote_NullReferenceCode_ReturnsError() {
        CreateCreditNoteRequest invalid = new CreateCreditNoteRequest(null, "2", "20", "SETP-01", 1776, null, List.of(), null, List.of());
        CreditNoteResult result = creditNoteClient.emitCreditNote(invalid, 5L);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("ERROR", result.status());
        assertTrue(result.errorMessage().contains("reference_code es obligatorio"));
    }

    @Test
    void emitCreditNote_SuccessfulValidation_ReturnsValidatedResult() {
        setupAccess();

        String responseJson = """
                {
                  "status": "Created",
                  "message": "Nota crédito creada y validada exitosamente",
                  "data": {
                    "credit_note": {
                      "number": "NC990000001",
                      "cude": "cude-hash-12345",
                      "qr": "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=cude-hash-12345",
                      "status": true
                    },
                    "links": {
                      "qr": "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=cude-hash-12345",
                      "public_url": "https://api-sandbox.factus.com.co/v2/credit-notes/NC990000001/public"
                    }
                  }
                }
                """;

        server.expect(requestTo(URL + "/v2/credit-notes/validate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-5"))
                .andExpect(jsonPath("$.reference_code").value("NOTA-2026-0015"))
                .andExpect(jsonPath("$.bill_number").value("SETP990002519"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        CreditNoteResult result = creditNoteClient.emitCreditNote(createSampleRequest(), 5L);

        assertTrue(result.isSuccess());
        assertEquals("VALIDATED", result.status());
        assertEquals("NC990000001", result.creditNoteNumber());
        assertEquals("cude-hash-12345", result.cude());
        assertNotNull(result.qrCode());
        assertNotNull(result.pdfUrl());
        server.verify();
    }

    @Test
    void emitCreditNote_Conflict409_ReturnsErrorWithConflictMessage() {
        setupAccess();

        server.expect(requestTo(URL + "/v2/credit-notes/validate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("{\"message\":\"Se encontró un documento pendiente por enviar a la DIAN. Error 409 Conflict\"}"));

        CreditNoteResult result = creditNoteClient.emitCreditNote(createSampleRequest(), 5L);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("ERROR", result.status());
        assertTrue(result.errorMessage().contains("409"));
        server.verify();
    }

    @Test
    void emitCreditNote_ValidationError422_ReturnsRejected() {
        setupAccess();

        server.expect(requestTo(URL + "/v2/credit-notes/validate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .body("{\"message\":\"El valor no coincide con el total de la factura\"}"));

        CreditNoteResult result = creditNoteClient.emitCreditNote(createSampleRequest(), 5L);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("REJECTED", result.status());
        assertTrue(result.errorMessage().contains("422"));
        server.verify();
    }

    @Test
    void emitCreditNote_Unauthorized401_InvalidatesToken() {
        setupAccess();

        server.expect(requestTo(URL + "/v2/credit-notes/validate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CreditNoteResult result = creditNoteClient.emitCreditNote(createSampleRequest(), 5L);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("PENDING", result.status());
        verify(tokenManager).invalidate(5L);
        server.verify();
    }

    @Test
    void deleteUnvalidatedCreditNote_Success_ReturnsTrue() {
        setupAccess();

        server.expect(requestTo(URL + "/v2/credit-notes/reference/NOTA-2026-0015"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer tok-5"))
                .andRespond(withNoContent());

        boolean deleted = creditNoteClient.deleteUnvalidatedCreditNote("NOTA-2026-0015", 5L);

        assertTrue(deleted);
        server.verify();
    }

    @Test
    void downloadCreditNotePDF_DecodesBase64Successfully() {
        setupAccess();

        String rawContent = "MOCK_PDF_CONTENT";
        String encoded = Base64.getEncoder().encodeToString(rawContent.getBytes());
        String responseJson = "{\"data\":{\"file_name\":\"NC990000001.pdf\",\"pdf_base_64_encoded\":\"" + encoded + "\"}}";

        server.expect(requestTo(URL + "/v2/credit-notes/NC990000001/download-pdf"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer tok-5"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        byte[] pdf = creditNoteClient.downloadCreditNotePDF("NC990000001", 5L);

        assertNotNull(pdf);
        assertEquals(rawContent, new String(pdf));
        server.verify();
    }

    @Test
    void downloadCreditNoteXML_DecodesBase64Successfully() {
        setupAccess();

        String rawContent = "<CreditNote>Legal</CreditNote>";
        String encoded = Base64.getEncoder().encodeToString(rawContent.getBytes());
        String responseJson = "{\"data\":{\"file_name\":\"NC990000001.xml\",\"xml_base_64_encoded\":\"" + encoded + "\"}}";

        server.expect(requestTo(URL + "/v2/credit-notes/NC990000001/download-xml"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer tok-5"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        byte[] xml = creditNoteClient.downloadCreditNoteXML("NC990000001", 5L);

        assertNotNull(xml);
        assertEquals(rawContent, new String(xml));
        server.verify();
    }
}
