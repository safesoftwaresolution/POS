package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.AlegraApiException;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccess;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccessResolver;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraClientHelper;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * El PDF de la factura lo entrega Alegra dentro de files.zip (verificado en el sandbox). Se pide siempre un enlace
 * fresco (caduca a los 60 min), el enlace no recibe el token y solo se confia en hosts de Alegra/S3 por https.
 */
class AlegraInvoicePdfTest {

    private static final String URL = "https://sandbox.example/e-provider/col/v1";
    private static final String TOKEN = "platform-token";
    private static final String ZIP_LINK = "https://sandbox-e-provider-col-api.alegra.com/v1/files/download?t=abc";
    private static final byte[] PDF = "%PDF-1.3\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);

    private MockRestServiceServer server;
    private AlegraInvoiceClient client;

    @BeforeEach
    void setUp() {
        AlegraAccessResolver resolver = mock(AlegraAccessResolver.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AlegraInvoiceClient(resolver, mock(AlegraCompanyProvider.class), mock(AlegraNumberAllocator.class),
                new AlegraInvoiceMapper(), new AlegraClientHelper(builder.build()));
        when(resolver.resolve(null)).thenReturn(new AlegraAccess(5L, BillingEnvironment.SANDBOX, URL, TOKEN, "company-1"));
        TenantContext.setStoreId(5L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static byte[] zip(String... namesAndContents) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zip.putNextEntry(new ZipEntry(namesAndContents[i]));
                zip.write(namesAndContents[i + 1].getBytes(StandardCharsets.ISO_8859_1));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] zipWithEntry(String name, byte[] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    /**
     * El zip real de Alegra: entradas SIN comprimir (STORED) y con descriptor de datos (bit 3), que ZipInputStream no
     * sabe leer. Se arma a mano porque ZipOutputStream no puede producirlo.
     */
    private static byte[] storedZipWithDataDescriptors(String[] names, byte[][] contents) {
        ByteBuffer out = ByteBuffer.allocate(1 << 20).order(ByteOrder.LITTLE_ENDIAN);
        int[] offsets = new int[names.length];
        long[] crcs = new long[names.length];
        for (int i = 0; i < names.length; i++) {
            byte[] name = names[i].getBytes(StandardCharsets.UTF_8);
            CRC32 crc = new CRC32();
            crc.update(contents[i]);
            crcs[i] = crc.getValue();
            offsets[i] = out.position();
            out.putInt(0x04034b50).putShort((short) 20).putShort((short) 0x0008).putShort((short) 0)
                    .putShort((short) 0).putShort((short) 0x21).putInt(0).putInt(0).putInt(0)
                    .putShort((short) name.length).putShort((short) 0).put(name).put(contents[i]);
            out.putInt(0x08074b50).putInt((int) crcs[i]).putInt(contents[i].length).putInt(contents[i].length);
        }
        int centralStart = out.position();
        for (int i = 0; i < names.length; i++) {
            byte[] name = names[i].getBytes(StandardCharsets.UTF_8);
            out.putInt(0x02014b50).putShort((short) 20).putShort((short) 20).putShort((short) 0x0008).putShort((short) 0)
                    .putShort((short) 0).putShort((short) 0x21).putInt((int) crcs[i]).putInt(contents[i].length)
                    .putInt(contents[i].length).putShort((short) name.length).putShort((short) 0).putShort((short) 0)
                    .putShort((short) 0).putShort((short) 0).putInt(0).putInt(offsets[i]).put(name);
        }
        int centralSize = out.position() - centralStart;
        out.putInt(0x06054b50).putShort((short) 0).putShort((short) 0).putShort((short) names.length)
                .putShort((short) names.length).putInt(centralSize).putInt(centralStart).putShort((short) 0);
        byte[] result = new byte[out.position()];
        out.flip();
        out.get(result);
        return result;
    }

    private void expectInvoice(String zipLink) {
        String zipJson = zipLink == null ? "" : ",\"zip\":\"" + zipLink + "\"";
        server.expect(once(), requestTo(URL + "/invoices/inv-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess("{\"invoice\":{\"id\":\"inv-1\",\"fullNumber\":\"SETP1\"},\"files\":{\"xml\":\"https://bucket.s3.us-east-1.amazonaws.com/a.xml\""
                        + zipJson + "}}", MediaType.APPLICATION_JSON));
    }

    private void expectZipDownload(byte[] body) {
        server.expect(once(), requestTo(ZIP_LINK))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(body, MediaType.parseMediaType("application/zip")));
    }

    @Test
    void returnsThePdfThatComesInsideTheZipWithoutSendingTheTokenToTheDownloadLink() throws Exception {
        expectInvoice(ZIP_LINK);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("ad0900559088.xml"));
            zip.write("<AttachedDocument/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("ad0900559088.pdf"));
            zip.write(PDF);
            zip.closeEntry();
        }
        expectZipDownload(out.toByteArray());

        byte[] result = client.downloadInvoicePDF("inv-1");

        assertArrayEquals(PDF, result);
        server.verify();
    }

    @Test
    void readsTheRealAlegraZipFormatStoredEntriesWithDataDescriptors() {
        expectInvoice(ZIP_LINK);
        expectZipDownload(storedZipWithDataDescriptors(
                new String[]{"ad0900559088.pdf", "ad0900559088.xml"},
                new byte[][]{PDF, "<AttachedDocument/>".getBytes(StandardCharsets.UTF_8)}));

        assertArrayEquals(PDF, client.downloadInvoicePDF("inv-1"));
    }

    @Test
    void aMissingZipLinkIsAClearError() {
        expectInvoice(null);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> client.downloadInvoicePDF("inv-1"));

        assertTrue(error.getMessage().contains("PDF"), error.getMessage());
    }

    @Test
    void aZipWithoutAPdfIsAClearError() throws Exception {
        expectInvoice(ZIP_LINK);
        expectZipDownload(zip("solo.xml", "<x/>"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> client.downloadInvoicePDF("inv-1"));

        assertTrue(error.getMessage().contains("PDF"), error.getMessage());
    }

    @Test
    void aFileNamedPdfThatIsNotAPdfIsRefused() throws Exception {
        expectInvoice(ZIP_LINK);
        expectZipDownload(zip("fake.pdf", "<html>no soy un pdf</html>"));

        assertThrows(IllegalStateException.class, () -> client.downloadInvoicePDF("inv-1"));
    }

    @Test
    void anOversizedPdfEntryIsRefused() throws Exception {
        expectInvoice(ZIP_LINK);
        byte[] huge = new byte[11 * 1024 * 1024];
        System.arraycopy("%PDF-1.3".getBytes(StandardCharsets.ISO_8859_1), 0, huge, 0, 8);
        expectZipDownload(zipWithEntry("big.pdf", huge));

        assertThrows(IllegalStateException.class, () -> client.downloadInvoicePDF("inv-1"));
    }

    @Test
    void onlyHttpsLinksOnAlegraOrS3HostsAreFollowed() {
        for (String link : new String[]{"https://evil.example/file.zip", "http://sandbox-e-provider-col-api.alegra.com/f.zip",
                "https://alegra.com.evil.example/f.zip", "https://alegra.com@evil.example/f.zip", "file:///etc/passwd"}) {
            server.reset();
            expectInvoice(link);

            assertThrows(IllegalStateException.class, () -> client.downloadInvoicePDF("inv-1"), link);
            server.verify(); // solo se hizo la primera llamada: el enlace sospechoso nunca se consulta
        }
    }

    @Test
    void aFailingDownloadIsAProviderError() {
        expectInvoice(ZIP_LINK);
        server.expect(once(), requestTo(ZIP_LINK)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        AlegraApiException error = assertThrows(AlegraApiException.class, () -> client.downloadInvoicePDF("inv-1"));

        assertEquals(403, error.getUpstreamStatus());
    }

    @Test
    void anUnknownInvoiceIsAProviderError() {
        server.expect(once(), requestTo(URL + "/invoices/inv-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"AEP2001\",\"message\":\"Document not found\"}]}"));

        AlegraApiException error = assertThrows(AlegraApiException.class, () -> client.downloadInvoicePDF("inv-1"));

        assertEquals(404, error.getUpstreamStatus());
    }

    @Test
    void aRedirectToAnUntrustedHostIsRefusedAndNeverCalled() {
        expectInvoice(ZIP_LINK);
        server.expect(once(), requestTo(ZIP_LINK))
                .andRespond(withStatus(HttpStatus.FOUND).header("Location", "https://evil.example/steal.zip"));

        assertThrows(IllegalStateException.class, () -> client.downloadInvoicePDF("inv-1"));

        server.verify(); // solo la primera llamada: el destino sospechoso nunca se consulta
    }

    @Test
    void theRealAlegraFlowFollowsTheRedirectToItsOwnBucketWithoutSendingTheToken() throws Exception {
        String bucket = "https://api-alegra-e-provider-col-sand.s3.us-east-1.amazonaws.com/inv.zip?X-Amz-Signature=abc";
        expectInvoice(ZIP_LINK);
        server.expect(once(), requestTo(ZIP_LINK))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withStatus(HttpStatus.FOUND).header("Location", bucket));
        server.expect(once(), requestTo(bucket))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(zip("doc.pdf", new String(PDF, StandardCharsets.ISO_8859_1)),
                        MediaType.parseMediaType("application/zip")));

        assertArrayEquals(PDF, client.downloadInvoicePDF("inv-1"));
        server.verify();
    }

    @Test
    void aRedirectWithoutLocationOrAnEndlessChainIsAFailure() {
        expectInvoice(ZIP_LINK);
        server.expect(once(), requestTo(ZIP_LINK)).andRespond(withStatus(HttpStatus.FOUND));
        AlegraApiException noLocation = assertThrows(AlegraApiException.class, () -> client.downloadInvoicePDF("inv-1"));
        assertEquals(302, noLocation.getUpstreamStatus());

        server.reset();
        expectInvoice(ZIP_LINK);
        server.expect(org.springframework.test.web.client.ExpectedCount.times(4), requestTo(ZIP_LINK))
                .andRespond(withStatus(HttpStatus.FOUND).header("Location", ZIP_LINK));
        assertThrows(IllegalStateException.class, () -> client.downloadInvoicePDF("inv-1"));
        server.verify();
    }

    @Test
    void aConnectionFailureNeverLeaksTheSignedLinkIntoTheMessage() {
        expectInvoice(ZIP_LINK);
        server.expect(once(), requestTo(ZIP_LINK)).andRespond(request -> { throw new IOException("connection reset"); });

        AlegraApiException error = assertThrows(AlegraApiException.class, () -> client.downloadInvoicePDF("inv-1"));

        assertFalse(error.getMessage().contains("t=abc"), error.getMessage());
        assertFalse(error.getMessage().contains("?t="), error.getMessage());
        assertTrue(error.isTemporary());
    }

    @Test
    void onlyAlegrasOwnS3BucketIsTrustedNotAnyAwsCustomer() throws Exception {
        String alegraBucket = "https://api-alegra-e-provider-col-sand.s3.us-east-1.amazonaws.com/f.zip?X-Amz-Signature=abc";
        expectInvoice(alegraBucket);
        server.expect(once(), requestTo(alegraBucket))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(zip("doc.pdf", new String(PDF, StandardCharsets.ISO_8859_1)),
                        MediaType.parseMediaType("application/zip")));
        assertArrayEquals(PDF, client.downloadInvoicePDF("inv-1"));

        server.reset();
        expectInvoice("https://evil-bucket.s3.amazonaws.com/f.zip");
        assertThrows(IllegalStateException.class, () -> client.downloadInvoicePDF("inv-1"));
        server.verify();
    }
}
