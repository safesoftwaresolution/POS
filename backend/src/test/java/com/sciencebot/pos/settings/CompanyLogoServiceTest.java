package com.sciencebot.pos.settings;

import com.sciencebot.pos.billing.internal.adapters.factus.FactusTokenManager;
import com.sciencebot.pos.config.TenantContext;
import com.sciencebot.pos.settings.internal.entities.Setting;
import com.sciencebot.pos.settings.internal.repositories.SettingsRepository;
import com.sciencebot.pos.settings.internal.services.CompanyLogoServiceImpl;
import com.sciencebot.pos.storage.StorageFacade;
import com.sciencebot.pos.storage.StorageUploadResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CompanyLogoServiceTest {

    private static final Long STORE_ID = 10L;
    private static final String MOCK_TOKEN = "mocked-jwt-token-12345";
    private static final String FACTUS_URL = "https://api-sandbox.factus.com.co";

    @Mock
    private SettingsRepository settingsRepository;

    @Mock
    private FactusTokenManager factusTokenManager;

    @Mock
    private StorageFacade storageFacade;

    private MockRestServiceServer mockServer;
    private CompanyLogoServiceImpl companyLogoService;
    private Setting mockSetting;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setStoreId(STORE_ID);

        when(factusTokenManager.getAccessToken()).thenReturn(MOCK_TOKEN);

        mockSetting = new Setting();
        mockSetting.setId(STORE_ID);
        mockSetting.setBusinessName("Mi Tienda POS");
        when(settingsRepository.findById(STORE_ID)).thenReturn(Optional.of(mockSetting));
        when(settingsRepository.save(any(Setting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        companyLogoService = new CompanyLogoServiceImpl(
                settingsRepository,
                factusTokenManager,
                storageFacade,
                restClient,
                FACTUS_URL
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void updateLogo_succeeds_withValidPngAndSyncsFactus() throws Exception {
        byte[] pngBytes = createImageBytes(200, 200, "png");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "mi_empresa_logo.png",
                "image/png",
                pngBytes
        );

        when(storageFacade.uploadFile(eq(file), eq("logos"))).thenReturn(
                new StorageUploadResult("logos/logo123.png", "https://pub.r2.dev/logos/logo123.png", "mi_empresa_logo.png", file.getSize(), "image/png")
        );

        mockServer.expect(requestTo(FACTUS_URL + "/v2/companies/logo"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + MOCK_TOKEN))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("{\"status\":\"success\",\"message\":\"Logo updated\"}", MediaType.APPLICATION_JSON));

        CompanyLogoResponseDto response = companyLogoService.updateLogo(file);

        assertNotNull(response);
        assertTrue(response.factusSynced());
        assertEquals("https://pub.r2.dev/logos/logo123.png", response.logoUrl());
        assertEquals("mi_empresa_logo.png", response.originalFilename());
        assertEquals(200, response.width());
        assertEquals(200, response.height());
        assertEquals("https://pub.r2.dev/logos/logo123.png", mockSetting.getLogoUrl());

        verify(settingsRepository, times(1)).save(mockSetting);
        mockServer.verify();
    }

    @Test
    void updateLogo_savesLocallyWithoutCallingFactus_whenStoreBillingIsNotActive() throws Exception {
        when(factusTokenManager.getAccessToken())
                .thenThrow(new com.sciencebot.pos.billing.BillingNotEnabledException("no activo"));
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", createImageBytes(100, 100, "png"));
        when(storageFacade.uploadFile(eq(file), eq("logos"))).thenReturn(
                new StorageUploadResult("logos/l.png", "https://pub.r2.dev/logos/l.png", "logo.png", file.getSize(), "image/png"));

        CompanyLogoResponseDto response = companyLogoService.updateLogo(file);

        assertFalse(response.factusSynced());
        assertTrue(response.message().contains("no está activa"));
        assertEquals("https://pub.r2.dev/logos/l.png", mockSetting.getLogoUrl());
        mockServer.verify(); // sin expectativas: falla si se llamó a Factus
    }

    @Test
    void updateLogo_usesTheFactusUrlOfTheStoreEnvironment() throws Exception {
        when(factusTokenManager.getBaseUrl()).thenReturn("https://api.factus.com.co/");
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", createImageBytes(100, 100, "png"));
        when(storageFacade.uploadFile(eq(file), eq("logos"))).thenReturn(
                new StorageUploadResult("logos/l.png", "https://pub.r2.dev/logos/l.png", "logo.png", file.getSize(), "image/png"));
        mockServer.expect(requestTo("https://api.factus.com.co/v2/companies/logo"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        CompanyLogoResponseDto response = companyLogoService.updateLogo(file);

        assertTrue(response.factusSynced());
        mockServer.verify();
    }

    @Test
    void updateLogo_succeeds_withValidJpgMaxDimensions300x300() throws Exception {
        byte[] jpgBytes = createImageBytes(300, 300, "jpg");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo_tienda.jpg",
                "image/jpeg",
                jpgBytes
        );

        mockServer.expect(requestTo(FACTUS_URL + "/v2/companies/logo"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + MOCK_TOKEN))
                .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));

        CompanyLogoResponseDto response = companyLogoService.updateLogo(file);

        assertNotNull(response);
        assertTrue(response.factusSynced());
        assertEquals(300, response.width());
        assertEquals(300, response.height());
        assertNotNull(mockSetting.getLogoUrl());
        mockServer.verify();
    }

    @Test
    void updateLogo_fails_whenFileIsNull() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> companyLogoService.updateLogo(null)
        );
        assertTrue(ex.getMessage().contains("no vacío"));
        verifyNoInteractions(storageFacade);
    }

    @Test
    void updateLogo_fails_whenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "logo.png", "image/png", new byte[0]);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> companyLogoService.updateLogo(emptyFile)
        );
        assertTrue(ex.getMessage().contains("no vacío"));
    }

    @Test
    void updateLogo_fails_whenFormatIsInvalidPdf() {
        byte[] dummyBytes = "Dummy PDF content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile pdfFile = new MockMultipartFile("file", "document.pdf", "application/pdf", dummyBytes);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> companyLogoService.updateLogo(pdfFile)
        );
        assertTrue(ex.getMessage().contains("Formato no permitido"));
    }

    @Test
    void updateLogo_fails_whenFormatIsInvalidGif() throws Exception {
        byte[] gifBytes = createImageBytes(100, 100, "gif");
        MockMultipartFile gifFile = new MockMultipartFile("file", "logo.gif", "image/gif", gifBytes);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> companyLogoService.updateLogo(gifFile)
        );
        assertTrue(ex.getMessage().contains("Formato no permitido"));
    }

    @Test
    void updateLogo_fails_whenSizeExceeds200KB() {
        byte[] largeBytes = new byte[200 * 1024 + 1]; // 204,801 bytes > 200 KB
        Arrays.fill(largeBytes, (byte) 1);
        MockMultipartFile largeFile = new MockMultipartFile("file", "large_logo.png", "image/png", largeBytes);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> companyLogoService.updateLogo(largeFile)
        );
        assertTrue(ex.getMessage().contains("superando el límite máximo permitido de 200 KB"));
    }

    @Test
    void updateLogo_fails_whenWidthExceeds300px() throws Exception {
        byte[] wideImageBytes = createImageBytes(301, 200, "png");
        MockMultipartFile wideFile = new MockMultipartFile("file", "wide_logo.png", "image/png", wideImageBytes);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> companyLogoService.updateLogo(wideFile)
        );
        assertTrue(ex.getMessage().contains("exceden el límite máximo de 300x300"));
    }

    @Test
    void updateLogo_fails_whenHeightExceeds300px() throws Exception {
        byte[] tallImageBytes = createImageBytes(200, 301, "png");
        MockMultipartFile tallFile = new MockMultipartFile("file", "tall_logo.png", "image/png", tallImageBytes);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> companyLogoService.updateLogo(tallFile)
        );
        assertTrue(ex.getMessage().contains("exceden el límite máximo de 300x300"));
    }

    @Test
    void updateLogo_fails_whenCorruptedImage() {
        byte[] fakeBytes = "Este es texto plano con extension .png".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile fakeFile = new MockMultipartFile("file", "fake_logo.png", "image/png", fakeBytes);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> companyLogoService.updateLogo(fakeFile)
        );
        assertTrue(ex.getMessage().contains("no es una imagen válida o está corrupto"));
    }

    @Test
    void updateLogo_fails_whenFactusReturnsServerError() throws Exception {
        byte[] pngBytes = createImageBytes(150, 150, "png");
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", pngBytes);

        mockServer.expect(requestTo(FACTUS_URL + "/v2/companies/logo"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body("{\"error\":\"Internal Server Error in Factus\"}"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> companyLogoService.updateLogo(file)
        );
        assertTrue(ex.getMessage().contains("Factus API rechazó el logo"));
        mockServer.verify();
    }

    private byte[] createImageBytes(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(image, format, os);
        return os.toByteArray();
    }
}
