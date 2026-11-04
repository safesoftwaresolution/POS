package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.AlegraApiException;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccess;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccessResolver;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraClientHelper;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraCompany;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraCompanyProvider;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceClient;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceMapper;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Envio por correo (POST /send-email) y descarga del XML (files.xml) de una factura de Alegra. */
class AlegraInvoiceEmailXmlTest {

    private static final String URL = "https://sandbox.example/e-provider/col/v1";
    private static final String TOKEN = "platform-token";
    private static final String XML_LINK = "https://api-alegra-e-provider-col-sand.s3.us-east-1.amazonaws.com/users/u/inv.xml?X-Amz-Signature=abc";
    private static final byte[] XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><AttachedDocument/>".getBytes(StandardCharsets.UTF_8);

    private AlegraCompanyProvider companyProvider;
    private MockRestServiceServer server;
    private AlegraInvoiceClient client;

    @BeforeEach
    void setUp() {
        AlegraAccessResolver resolver = mock(AlegraAccessResolver.class);
        companyProvider = mock(AlegraCompanyProvider.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AlegraInvoiceClient(resolver, companyProvider, mock(AlegraNumberAllocator.class),
                new AlegraInvoiceMapper(), new AlegraClientHelper(builder.build()));
        AlegraAccess access = new AlegraAccess(5L, BillingEnvironment.SANDBOX, URL, TOKEN, "company-1");
        when(resolver.resolve(null)).thenReturn(access);
        when(companyProvider.get(access)).thenReturn(company("facturacion@tienda.co"));
        TenantContext.setStoreId(5L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static AlegraCompany company(String email) {
        return new AlegraCompany("company-1", "Tienda SAS", null, "900559088", "2", "R-99-PN", 1, "31", email, null, null, Map.of());
    }

    private void expectInvoice(String xmlLink) {
        String xml = xmlLink == null ? "\"xml\":null" : "\"xml\":\"" + xmlLink + "\"";
        server.expect(once(), requestTo(URL + "/invoices/inv-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess("{\"invoice\":{\"id\":\"inv-1\"},\"files\":{" + xml + "}}", MediaType.APPLICATION_JSON));
    }

    // ---------------- correo ----------------

    @Test
    void sendsTheEmailForTheProviderDocumentWithTheCustomerInBothAddressesBecauseAlegraDeliversToReplyTo() {
        server.expect(once(), requestTo(URL + "/send-email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(jsonPath("$.documentType").value("01"))
                .andExpect(jsonPath("$.documentId").value("inv-1"))
                .andExpect(jsonPath("$.email.to").value("cliente@correo.co"))
                .andExpect(jsonPath("$.email.replyTo").value("cliente@correo.co"))
                .andRespond(withSuccess("{\"message\":\"Email sent\",\"data\":{}}", MediaType.APPLICATION_JSON));

        assertTrue(client.sendInvoiceEmail("inv-1", "cliente@correo.co"));
        server.verify();
    }

    @Test
    void theCompanyEmailIsNeverUsedAsReplyToEvenWhenItExistsOrTheInvoiceWouldGoToTheCompany() {
        server.expect(once(), requestTo(URL + "/send-email"))
                .andExpect(jsonPath("$.email.replyTo").value("otro@cliente.co"))
                .andExpect(jsonPath("$.email.to").value("otro@cliente.co"))
                .andRespond(withSuccess("{\"message\":\"Email sent\"}", MediaType.APPLICATION_JSON));

        assertTrue(client.sendInvoiceEmail("inv-1", "otro@cliente.co"));
        server.verify();
    }

    @Test
    void anInvalidReplyToOrAMissingDocumentAreProviderErrorsWithTheirMessage() {
        server.expect(once(), requestTo(URL + "/send-email"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"AEP7001\",\"message\":\"replyTo must be a valid email\"}]}"));
        AlegraApiException badRequest = assertThrows(AlegraApiException.class, () -> client.sendInvoiceEmail("inv-1", "x"));
        assertEquals(400, badRequest.getUpstreamStatus());
        assertTrue(badRequest.getMessage().contains("AEP7001"), badRequest.getMessage());

        server.reset();
        server.expect(once(), requestTo(URL + "/send-email"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"AEP2001\",\"message\":\"Document not found\"}]}"));
        AlegraApiException notFound = assertThrows(AlegraApiException.class, () -> client.sendInvoiceEmail("inv-1", "a@b.co"));
        assertEquals(404, notFound.getUpstreamStatus());
        assertFalse(notFound.getMessage().contains(TOKEN));
    }

    // ---------------- XML ----------------

    @Test
    void downloadsTheXmlFromTheTemporaryLinkWithoutSendingTheToken() {
        expectInvoice(XML_LINK);
        server.expect(once(), requestTo(XML_LINK))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(XML, MediaType.TEXT_XML));

        assertArrayEquals(XML, client.downloadInvoiceXML("inv-1"));
        server.verify();
    }

    @Test
    void aNullXmlLinkIsAskedForAgainOnceBeforeGivingUp() {
        expectInvoice(null);
        expectInvoice(XML_LINK);
        server.expect(once(), requestTo(XML_LINK)).andRespond(withSuccess(XML, MediaType.TEXT_XML));

        assertArrayEquals(XML, client.downloadInvoiceXML("inv-1"));
        server.verify();
    }

    @Test
    void ifAlegraNeverGivesTheXmlTheErrorSaysSo() {
        expectInvoice(null);
        expectInvoice(null);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> client.downloadInvoiceXML("inv-1"));

        assertTrue(error.getMessage().contains("XML"), error.getMessage());
        server.verify();
    }

    @Test
    void aDownloadThatIsNotXmlOrComesFromAnUntrustedHostIsRefused() {
        expectInvoice(XML_LINK);
        server.expect(once(), requestTo(XML_LINK)).andRespond(withSuccess("<html>no es xml</html>".getBytes(StandardCharsets.UTF_8), MediaType.TEXT_HTML));
        assertThrows(IllegalStateException.class, () -> client.downloadInvoiceXML("inv-1"));

        server.reset();
        expectInvoice("https://evil.example/inv.xml");
        assertThrows(IllegalStateException.class, () -> client.downloadInvoiceXML("inv-1"));
        server.verify();
    }

    @Test
    void anUnknownInvoiceIsAProviderError() {
        server.expect(once(), requestTo(URL + "/invoices/inv-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"AEP2001\",\"message\":\"Document not found\"}]}"));

        AlegraApiException error = assertThrows(AlegraApiException.class, () -> client.downloadInvoiceXML("inv-1"));

        assertEquals(404, error.getUpstreamStatus());
    }
}
