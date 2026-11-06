package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.AlegraApiException;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccessResolver;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraClientHelper;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraOnboardingClient;
import com.sciencebot.pos.billing.internal.adapters.dto.CompanyProfileData;
import com.sciencebot.pos.billing.internal.adapters.dto.DianEnablementStatus;
import com.sciencebot.pos.billing.internal.adapters.dto.DianResolutionData;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Alta de la empresa del local en Alegra y su habilitación ante la DIAN, con el token de plataforma del entorno. */
class AlegraOnboardingClientTest {

    private static final String SANDBOX_URL = "https://sandbox.example/e-provider/col/v1";
    private static final String PRODUCTION_URL = "https://prod.example/e-provider/col/v1";
    private static final String PRODUCTION_TOKEN = "production-platform-token";
    private static final String COMPANY_ID = "01M6KH7R1QZQK687BNMX5BBXZC";

    private MockRestServiceServer server;
    private AlegraOnboardingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AlegraAccessResolver resolver = new AlegraAccessResolver(mock(StoreBillingConfigRepository.class),
                SANDBOX_URL, PRODUCTION_URL, "", PRODUCTION_TOKEN);
        client = new AlegraOnboardingClient(new AlegraClientHelper(builder.build()), resolver);
    }

    private static CompanyProfileData profile() {
        return new CompanyProfileData("Tienda Don Pepe SAS", "Don Pepe", "901234567", "8", 1, "31", "O-13;R-99-PN",
                List.of("4711", "4721"), "dueno@donpepe.co", "3001234567", "Calle 10 # 20-30", "05001", "05");
    }

    @Test
    void createCompany_PostsTheFiscalProfileWithTheAlegraCertificateAndReturnsTheCompanyId() {
        server.expect(requestTo(PRODUCTION_URL + "/companies"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + PRODUCTION_TOKEN))
                .andExpect(jsonPath("$.name").value("Tienda Don Pepe SAS"))
                .andExpect(jsonPath("$.tradeName").value("Don Pepe"))
                .andExpect(jsonPath("$.identification").value("901234567"))
                .andExpect(jsonPath("$.dv").value("8"))
                .andExpect(jsonPath("$.useAlegraCertificate").value(true))
                .andExpect(jsonPath("$.type").value("associated"))
                .andExpect(jsonPath("$.organizationType").value(1))
                .andExpect(jsonPath("$.identificationType").value("31"))
                .andExpect(jsonPath("$.regimeCode").value("O-13;R-99-PN"))
                .andExpect(jsonPath("$.economicActivities", contains("4711", "4721")))
                .andExpect(jsonPath("$.email").value("dueno@donpepe.co"))
                .andExpect(jsonPath("$.phone").value("3001234567"))
                .andExpect(jsonPath("$.address.address").value("Calle 10 # 20-30"))
                .andExpect(jsonPath("$.address.city").value("05001"))
                .andExpect(jsonPath("$.address.department").value("05"))
                .andExpect(jsonPath("$.address.country").value("CO"))
                .andExpect(jsonPath("$.notificationByEmail.enabled").value(true))
                .andRespond(withSuccess("{\"company\":{\"id\":\"" + COMPANY_ID + "\",\"name\":\"Tienda Don Pepe SAS\"}}",
                        MediaType.APPLICATION_JSON));

        assertEquals(COMPANY_ID, client.createCompany(BillingEnvironment.PRODUCTION, profile()));
        server.verify();
    }

    @Test
    void createCompany_OmitsTheOptionalFieldsThatAreMissing() {
        CompanyProfileData minimal = new CompanyProfileData("Tienda Don Pepe SAS", null, "901234567", "8", null, null,
                null, null, null, null, null, null, null);
        server.expect(requestTo(PRODUCTION_URL + "/companies"))
                .andExpect(jsonPath("$.useAlegraCertificate").value(true))
                .andExpect(jsonPath("$.tradeName").doesNotExist())
                .andExpect(jsonPath("$.economicActivities").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andRespond(withSuccess("{\"company\":{\"id\":\"" + COMPANY_ID + "\"}}", MediaType.APPLICATION_JSON));

        assertEquals(COMPANY_ID, client.createCompany(BillingEnvironment.PRODUCTION, minimal));
        server.verify();
    }

    @Test
    void createCompany_AlsoReadsATopLevelId() {
        server.expect(requestTo(PRODUCTION_URL + "/companies"))
                .andRespond(withSuccess("{\"id\":\"" + COMPANY_ID + "\"}", MediaType.APPLICATION_JSON));

        assertEquals(COMPANY_ID, client.createCompany(BillingEnvironment.PRODUCTION, profile()));
    }

    @Test
    void createCompany_WithoutAnIdInTheResponseFails() {
        server.expect(requestTo(PRODUCTION_URL + "/companies"))
                .andRespond(withSuccess("{\"company\":{}}", MediaType.APPLICATION_JSON));

        assertThrows(AlegraApiException.class, () -> client.createCompany(BillingEnvironment.PRODUCTION, profile()));
    }

    @Test
    void createCompany_TranslatesAlegraErrorsWithoutLeakingTheToken() {
        server.expect(requestTo(PRODUCTION_URL + "/companies"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"AEP1001\",\"message\":\"identification already exists\"}]}"));

        AlegraApiException ex = assertThrows(AlegraApiException.class,
                () -> client.createCompany(BillingEnvironment.PRODUCTION, profile()));
        assertEquals(400, ex.getUpstreamStatus());
        assertTrue(ex.getMessage().contains("AEP1001 identification already exists"), ex.getMessage());
        assertFalse(ex.getMessage().contains(PRODUCTION_TOKEN));
    }

    @Test
    void updateCompany_PatchesTheCompany() {
        server.expect(requestTo(PRODUCTION_URL + "/companies/" + COMPANY_ID))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("Authorization", "Bearer " + PRODUCTION_TOKEN))
                .andExpect(jsonPath("$.name").value("Tienda Don Pepe SAS"))
                .andExpect(jsonPath("$.address.country").value("CO"))
                .andExpect(jsonPath("$.notificationByEmail.enabled").value(true))
                .andRespond(withSuccess("{\"company\":{\"id\":\"" + COMPANY_ID + "\"}}", MediaType.APPLICATION_JSON));

        client.updateCompany(BillingEnvironment.PRODUCTION, COMPANY_ID, profile());
        server.verify();
    }

    @Test
    void startDianEnablement_PostsTheInvoicesTestSetForTheCompany() {
        server.expect(requestTo(PRODUCTION_URL + "/test-sets"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + PRODUCTION_TOKEN))
                .andExpect(jsonPath("$.type").value("invoices"))
                .andExpect(jsonPath("$.governmentId").value("a1b2c3d4-test-set"))
                .andExpect(jsonPath("$.company.id").value(COMPANY_ID))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.startDianEnablement(BillingEnvironment.PRODUCTION, COMPANY_ID, " a1b2c3d4-test-set ");
        server.verify();
    }

    @Test
    void startDianEnablement_TranslatesTheProviderError() {
        server.expect(requestTo(PRODUCTION_URL + "/test-sets"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"AEP3001\",\"message\":\"invalid governmentId\"}]}"));

        AlegraApiException ex = assertThrows(AlegraApiException.class,
                () -> client.startDianEnablement(BillingEnvironment.PRODUCTION, COMPANY_ID, "bad"));
        assertTrue(ex.getMessage().contains("AEP3001 invalid governmentId"), ex.getMessage());
    }

    @Test
    void readEnablement_ReadsTheInvoicesGovernmentStatus() {
        server.expect(requestTo(PRODUCTION_URL + "/companies/" + COMPANY_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + PRODUCTION_TOKEN))
                .andRespond(withSuccess("{\"company\":{\"id\":\"" + COMPANY_ID
                        + "\",\"governmentStatus\":{\"invoices\":\"AUTHORIZED\",\"payroll\":\"PENDING\"}}}",
                        MediaType.APPLICATION_JSON));

        DianEnablementStatus status = client.readEnablement(BillingEnvironment.PRODUCTION, COMPANY_ID);

        assertTrue(status.isAuthorized());
        assertEquals("AUTHORIZED", status.rawValue());
    }

    @Test
    void readEnablement_WithoutGovernmentStatusIsNotStarted() {
        server.expect(requestTo(PRODUCTION_URL + "/companies/" + COMPANY_ID))
                .andRespond(withSuccess("{\"company\":{\"id\":\"" + COMPANY_ID + "\"}}", MediaType.APPLICATION_JSON));

        assertEquals(DianEnablementStatus.Stage.NOT_STARTED,
                client.readEnablement(BillingEnvironment.PRODUCTION, COMPANY_ID).stage());
    }

    @Test
    void listResolutions_ParsesTheDianResolutionsOfTheNit() {
        server.expect(requestTo(PRODUCTION_URL + "/resolutions/901234567"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + PRODUCTION_TOKEN))
                .andRespond(withSuccess("{\"resolutions\":[{\"resolutionNumber\":\"18764000000001\",\"prefix\":\"FE\","
                        + "\"minNumber\":1,\"maxNumber\":5000,\"startDate\":\"2026-01-15\",\"endDate\":\"2028-01-15\","
                        + "\"technicalKey\":\"abc123\",\"futureField\":true}]}", MediaType.APPLICATION_JSON));

        List<DianResolutionData> resolutions = client.listResolutions(BillingEnvironment.PRODUCTION, "901234567");

        assertEquals(List.of(new DianResolutionData("18764000000001", "FE", 1L, 5000L,
                LocalDate.of(2026, 1, 15), LocalDate.of(2028, 1, 15), "abc123")), resolutions);
    }

    @Test
    void listResolutions_WhenTheDianHasNotEnabledTheCompanyYetSaysSoClearly() {
        server.expect(requestTo(PRODUCTION_URL + "/resolutions/901234567"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"AEP9009\",\"message\":\"Company not enabled\"}]}"));

        BillingNotEnabledException ex = assertThrows(BillingNotEnabledException.class,
                () -> client.listResolutions(BillingEnvironment.PRODUCTION, "901234567"));
        assertEquals("La DIAN todavía no habilita a esta empresa con Alegra", ex.getMessage());
    }

    @Test
    void listResolutions_WhenTheNitIsNotOneOfThePlatformCompaniesSaysSo() {
        server.expect(requestTo(PRODUCTION_URL + "/resolutions/901234567"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("[{\"code\":\"AEP9008\",\"message\":\"Identification not found\"}]"));

        BillingNotEnabledException ex = assertThrows(BillingNotEnabledException.class,
                () -> client.listResolutions(BillingEnvironment.PRODUCTION, "901234567"));
        assertEquals("El NIT 901234567 no corresponde a ninguna empresa creada en Alegra por la plataforma",
                ex.getMessage());
    }

    @Test
    void withoutThePlatformTokenOfTheEnvironmentNothingIsCalled() {
        BillingNotEnabledException ex = assertThrows(BillingNotEnabledException.class,
                () -> client.createCompany(BillingEnvironment.SANDBOX, profile()));
        assertTrue(ex.getMessage().startsWith("La facturación electrónica aún no está disponible en la plataforma"),
                ex.getMessage());
        assertFalse(client.isPlatformReady(BillingEnvironment.SANDBOX));
        assertTrue(client.isPlatformReady(BillingEnvironment.PRODUCTION));
        server.verify();
    }
}
