package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest.CustomerCreditNoteRequest;
import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest.ItemCreditNoteRequest;
import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest.ItemCreditNoteRequest.TaxItemRequest;
import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest.PaymentDetailRequest;
import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccess;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccessResolver;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraClientHelper;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraCompany;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraCompanyProvider;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceClient;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceMapper;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNoteClient;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator.Allocation;
import com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult;
import com.sciencebot.pos.billing.internal.adapters.dto.CustomerBillingData;
import com.sciencebot.pos.billing.internal.adapters.dto.DebitNoteResult;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.BillingDocumentContext;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.mappers.BillingCanonicalMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleFacade;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Notas credito y debito en Alegra: misma disciplina que las facturas (llave, numero, estado legal) sobre una factura aceptada. */
class AlegraNoteClientTest {

    private static final String URL = "https://sandbox.example/e-provider/col/v1";
    private static final String TOKEN = "platform-token";

    private AlegraNumberAllocator allocator;
    private ElectronicInvoiceRepository invoiceRepository;
    private SaleFacade saleFacade;
    private BillingCanonicalMapper canonicalMapper;
    private MockRestServiceServer server;
    private AlegraNoteClient client;
    private AlegraAccessResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = mock(AlegraAccessResolver.class);
        AlegraCompanyProvider companyProvider = mock(AlegraCompanyProvider.class);
        allocator = mock(AlegraNumberAllocator.class);
        invoiceRepository = mock(ElectronicInvoiceRepository.class);
        saleFacade = mock(SaleFacade.class);
        canonicalMapper = mock(BillingCanonicalMapper.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AlegraClientHelper helper = new AlegraClientHelper(builder.build());
        AlegraInvoiceMapper mapper = new AlegraInvoiceMapper();
        AlegraInvoiceClient invoiceClient = new AlegraInvoiceClient(resolver, companyProvider, allocator, mapper, helper);
        client = new AlegraNoteClient(resolver, companyProvider, allocator, mapper, helper, invoiceClient, invoiceRepository,
                saleFacade, canonicalMapper);

        AlegraAccess access = new AlegraAccess(5L, BillingEnvironment.SANDBOX, URL, TOKEN, "company-1");
        when(resolver.resolve(5L)).thenReturn(access);
        when(resolver.resolveDocument(any())).thenReturn(access);
        when(resolver.resolveDocumentForEmission(any())).thenReturn(access);
        when(companyProvider.get(access)).thenReturn(new AlegraCompany("company-1", "Empresa", null, "900559088", "2",
                "R-99-PN", 1, "31", "a@b.co", null, null, Map.of()));
        ElectronicInvoice invoice = ElectronicInvoice.initialize(7L, 5L);
        invoice.markAsValidated("SETP993836500", "cufe-abc", "qr", null, LocalDateTime.of(2026, 11, 4, 10, 30));
        invoice.trackProviderDocument("inv-1");
        when(invoiceRepository.findByStoreIdAndLegalNumber(5L, "SETP993836500")).thenReturn(Optional.of(invoice));
        when(allocator.allocate(eq(5L), eq(BillingEnvironment.SANDBOX), anyString(), anyString()))
                .thenReturn(allocation(false, "key-1"));
    }

    private static Allocation allocation(boolean completed, String key) {
        return new Allocation(11L, 2L, "NC", 700_000_123L, null, 1L, 999_999_999L, null, null, null, key, completed);
    }

    @Test
    void pendingCreditNoteIsQueriedWithoutAnotherPost() {
        var context = new BillingDocumentContext(5L, BillingEnvironment.SANDBOX, "alegra", "company-1");
        var original = allocation(false, "key-1");
        var pending = new Allocation(original.id(), original.rangeId(), original.prefix(), original.number(),
                original.resolutionNumber(), original.minNumber(), original.maxNumber(), original.startDate(),
                original.endDate(), original.technicalKey(), original.idempotencyKey(), false, "PENDING", "note-9", "{}", null);
        when(allocator.findAllocation(5L, BillingEnvironment.SANDBOX, "91", "NC-FAC-1-C2")).thenReturn(Optional.of(pending));
        server.expect(requestTo(URL + "/credit-notes/note-9"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response("creditNote", "SENT", "ACCEPTED"), MediaType.APPLICATION_JSON));

        var result = client.retryCreditNote("NC-FAC-1-C2", context);

        assertEquals("VALIDATED", result.status());
        assertEquals("note-9", result.providerDocumentId());
        verify(allocator, never()).rotateIdempotencyKey(any());
        verify(allocator).recordResult(eq(11L), any());
        server.verify();
    }

    @Test
    void noteXmlUsesItsBoundEndpointAndSafeDownloaderWithoutSendingCredentialsToFileLink() {
        var context = new BillingDocumentContext(5L, BillingEnvironment.SANDBOX, "alegra", "company-1");
        String link = "https://sandbox-api.alegra.com/files/note.xml";
        server.expect(requestTo(URL + "/debit-notes/note-9"))
                .andRespond(withSuccess("{\"files\":{\"xml\":\"" + link + "\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(link)).andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("<?xml version=\"1.0\"?><DebitNote/>", MediaType.APPLICATION_XML));

        byte[] xml = client.download("/debit-notes", "note-9", context, false);

        assertTrue(new String(xml, java.nio.charset.StandardCharsets.UTF_8).contains("DebitNote"));
        verify(resolver).resolveDocument(context);
        server.verify();
    }

    private static CreateCreditNoteRequest credit(String bill) {
        ItemCreditNoteRequest item = new ItemCreditNoteRequest("REF-A", "Producto", "1.00", "0.00", "10000.00", "94", "999",
                List.of(new TaxItemRequest("01", "19.00")));
        return new CreateCreditNoteRequest("NC-FAC-1-C2", "2", null, bill, null, "obs",
                List.of(new PaymentDetailRequest("1", "10", "pago-7", "11900.00")), null, List.of(item));
    }

    private static CreateDebitNoteRequest debit() {
        CreateDebitNoteRequest.ItemDebitNoteRequest item = new CreateDebitNoteRequest.ItemDebitNoteRequest("REF-I", "Intereses",
                "1.00", "0.00", "1000.00", "94", "999", List.of(new CreateDebitNoteRequest.ItemDebitNoteRequest.TaxItemRequest("01", "19.00")));
        return new CreateDebitNoteRequest("ND-FAC-1-C1", "1", null, "SETP993836500", null, "obs",
                List.of(new CreateDebitNoteRequest.PaymentDetailRequest("1", "10", "pago-7", null, "1190.00")), null, List.of(item));
    }

    private static String response(String key, String status, String legalStatus, String... errorMessages) {
        String errors = String.join(",", java.util.Arrays.stream(errorMessages).map(m -> "\"" + m + "\"").toList());
        return "{\"" + key + "\":{\"id\":\"note-9\",\"status\":\"" + status + "\""
                + (legalStatus == null ? "" : ",\"legalStatus\":\"" + legalStatus + "\"")
                + ",\"cude\":\"cude-xyz\",\"prefix\":\"NC\",\"number\":700000123,\"fullNumber\":\"NC700000123\","
                + "\"qrCodeContent\":\"qr-note\",\"governmentResponse\":{\"code\":\"00\",\"message\":\"ok\",\"errorMessages\":["
                + errors + "]}},\"files\":{}}";
    }

    private void expectPost(String endpoint, org.springframework.test.web.client.ResponseCreator creator) {
        server.expect(once(), requestTo(URL + endpoint))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(header("Idempotency-Key", "key-1"))
                .andExpect(jsonPath("$.prefix").value("NC"))
                .andExpect(jsonPath("$.number").value(700_000_123))
                .andExpect(jsonPath("$.associatedDocuments[0].uuid").value("cufe-abc"))
                .andExpect(jsonPath("$.associatedDocuments[0].prefix").value("SETP"))
                .andExpect(jsonPath("$.associatedDocuments[0].number").value(993_836_500))
                .andExpect(jsonPath("$.invoicePeriod.startDate").value("2026-11-04"))
                .andExpect(jsonPath("$.resolution").doesNotExist())
                .andRespond(creator);
    }

    private void saleCustomer() {
        SaleDto sale = mock(SaleDto.class);
        when(saleFacade.getById(7L)).thenReturn(Optional.of(sale));
        InvoiceRequest invoiceRequest = mock(InvoiceRequest.class);
        when(invoiceRequest.customer()).thenReturn(new CustomerBillingData("13", "1234567", "", "2", "ZZ",
                List.of("R-99-PN"), "Cliente Real", null, null, "real@c.co", null, null, "CO", "11001"));
        when(canonicalMapper.toInvoiceRequest(sale)).thenReturn(invoiceRequest);
    }

    @Test
    void creditNoteAccepted_IsSuccessWithTheCudeTheNumberAndTheProviderIdAndCompletesTheAllocation() {
        saleCustomer();
        expectPost("/credit-notes", withSuccess(response("creditNote", "SENT", "ACCEPTED"), MediaType.APPLICATION_JSON));

        CreditNoteResult result = client.emitCreditNote(credit("SETP993836500"), 5L);

        assertTrue(result.isSuccess());
        assertEquals("VALIDATED", result.status());
        assertEquals("NC700000123", result.creditNoteNumber());
        assertEquals("cude-xyz", result.cude());
        assertEquals("qr-note", result.qrCode());
        assertEquals("note-9", result.providerDocumentId());
        verify(allocator).allocate(5L, BillingEnvironment.SANDBOX, "91", "NC-FAC-1-C2");
        verify(allocator).markCompleted(11L);
        server.verify();
    }

    @Test
    void debitNoteAccepted_UsesTheDebitEndpointAndTheDocument92() {
        saleCustomer();
        when(allocator.allocate(eq(5L), eq(BillingEnvironment.SANDBOX), eq("92"), anyString())).thenReturn(allocation(false, "key-1"));
        expectPost("/debit-notes", withSuccess(response("debitNote", "SENT", "ACCEPTED_WITH_OBSERVATIONS", "Regla: DAK55"), MediaType.APPLICATION_JSON));

        DebitNoteResult result = client.emitDebitNote(debit(), 5L);

        assertTrue(result.isSuccess());
        assertEquals("cude-xyz", result.cude());
        assertEquals("note-9", result.providerDocumentId());
        verify(allocator).allocate(5L, BillingEnvironment.SANDBOX, "92", "ND-FAC-1-C1");
    }

    @Test
    void theCustomerComesFromTheOriginalSaleWhenTheRequestHasNone() {
        saleCustomer();
        server.expect(once(), requestTo(URL + "/credit-notes"))
                .andExpect(jsonPath("$.customer.name").value("Cliente Real"))
                .andExpect(jsonPath("$.customer.identificationNumber").value("1234567"))
                .andRespond(withSuccess(response("creditNote", "SENT", "ACCEPTED"), MediaType.APPLICATION_JSON));

        client.emitCreditNote(credit("SETP993836500"), 5L);

        server.verify();
    }

    @Test
    void aCustomerInTheRequestIsUsedAsIs() {
        CustomerCreditNoteRequest customer = new CustomerCreditNoteRequest("31", "900123456", "7", "Cliente SAS", null, null,
                null, "c@c.co", null, "1", "01", "CO", List.of("O-13"), "11001");
        CreateCreditNoteRequest base = credit("SETP993836500");
        CreateCreditNoteRequest withCustomer = new CreateCreditNoteRequest(base.referenceCode(), "2", null, base.billNumber(),
                null, "obs", base.paymentDetails(), customer, base.items());
        server.expect(once(), requestTo(URL + "/credit-notes"))
                .andExpect(jsonPath("$.customer.name").value("Cliente SAS"))
                .andExpect(jsonPath("$.customer.identificationType").value("31"))
                .andRespond(withSuccess(response("creditNote", "SENT", "ACCEPTED"), MediaType.APPLICATION_JSON));

        client.emitCreditNote(withCustomer, 5L);

        verifyNoInteractions(saleFacade);
        server.verify();
    }

    @Test
    void rejectedByTheDian_IsRejectedWithTheReasons() {
        saleCustomer();
        expectPost("/credit-notes", withSuccess(response("creditNote", "SENT", "REJECTED", "Regla: ZZ1, Rechazo: x"), MediaType.APPLICATION_JSON));

        CreditNoteResult result = client.emitCreditNote(credit("SETP993836500"), 5L);

        assertEquals("REJECTED", result.status());
        assertTrue(result.errorMessage().contains("Regla: ZZ1"));
        verify(allocator).markCompleted(11L);
    }

    @Test
    void waitingIsPendingAndKeepsTheProviderId() {
        saleCustomer();
        expectPost("/credit-notes", withSuccess(response("creditNote", "WAITING_RESPONSE", null), MediaType.APPLICATION_JSON));

        CreditNoteResult result = client.emitCreditNote(credit("SETP993836500"), 5L);

        assertEquals("PENDING", result.status());
        assertEquals("note-9", result.providerDocumentId());
        verify(allocator, never()).markCompleted(any());
    }

    @Test
    void badRequestIsRejectedAndServerErrorsAndTimeoutsStayPendingWithoutTheLiteral409() {
        saleCustomer();
        expectPost("/credit-notes", withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"errors\":[{\"code\":\"AEP6012\",\"message\":\"organizationType is required\"}]}"));
        assertEquals("REJECTED", client.emitCreditNote(credit("SETP993836500"), 5L).status());

        for (var creator : List.of(
                withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON).body("{}"),
                withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"IDEMPOTENCY_REQUEST_IN_PROGRESS\",\"message\":\"in progress\"}]}"),
                (org.springframework.test.web.client.ResponseCreator) request -> { throw new IOException("read timed out"); })) {
            server.reset();
            expectPost("/credit-notes", creator);
            CreditNoteResult result = client.emitCreditNote(credit("SETP993836500"), 5L);
            assertEquals("PENDING", result.status(), result.errorMessage());
            assertFalse(result.errorMessage().contains("409"), result.errorMessage());
            assertFalse(result.errorMessage().contains(TOKEN));
        }
    }

    @Test
    void anOriginalInvoiceThatDoesNotExistOrIsNotValidatedFailsWithoutSpendingANumber() {
        CreditNoteResult missing = client.emitCreditNote(credit("SETP-NOEXISTE"), 5L);
        assertEquals("ERROR", missing.status());
        assertTrue(missing.errorMessage().contains("SETP-NOEXISTE"), missing.errorMessage());

        ElectronicInvoice pending = ElectronicInvoice.initialize(8L, 5L);
        when(invoiceRepository.findByStoreIdAndLegalNumber(5L, "SETP-PEND")).thenReturn(Optional.of(pending));
        assertEquals("ERROR", client.emitCreditNote(credit("SETP-PEND"), 5L).status());

        verifyNoInteractions(allocator);
        server.verify();
    }

    @Test
    void afterAFinalNonAcceptedOutcomeTheKeyIsRotatedBeforeSending() {
        saleCustomer();
        Allocation old = allocation(true, "old");
        when(allocator.allocate(eq(5L), eq(BillingEnvironment.SANDBOX), eq("91"), anyString()))
                .thenReturn(new Allocation(old.id(), old.rangeId(), old.prefix(), old.number(), old.resolutionNumber(),
                        old.minNumber(), old.maxNumber(), old.startDate(), old.endDate(), old.technicalKey(),
                        old.idempotencyKey(), true, "REJECTED", "rejected-note", null, null));
        when(allocator.rotateIdempotencyKey(11L)).thenReturn(allocation(false, "key-1"));
        expectPost("/credit-notes", withSuccess(response("creditNote", "SENT", "ACCEPTED"), MediaType.APPLICATION_JSON));

        assertTrue(client.emitCreditNote(credit("SETP993836500"), 5L).isSuccess());

        verify(allocator).rotateIdempotencyKey(11L);
    }

    @Test
    void aReplayIsRefreshedWithTheCurrentStateOfTheNote() {
        saleCustomer();
        expectPost("/credit-notes", withSuccess(response("creditNote", "WAITING_RESPONSE", null), MediaType.APPLICATION_JSON)
                .header("Idempotent-Replayed", "true"));
        server.expect(once(), requestTo(URL + "/credit-notes/note-9"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response("creditNote", "SENT", "REJECTED", "Regla: 90"), MediaType.APPLICATION_JSON));

        CreditNoteResult result = client.emitCreditNote(credit("SETP993836500"), 5L);

        assertEquals("REJECTED", result.status());
        server.verify();
    }
}
