package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceClient;
import com.sciencebot.pos.billing.internal.adapters.dto.CustomerBillingData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import com.sciencebot.pos.billing.internal.entities.NumberingRange;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.repositories.DocumentNumberAllocationRepository;
import com.sciencebot.pos.billing.internal.repositories.NumberingRangeRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de punta a punta contra el SANDBOX real de Alegra. Solo corre si la variable de entorno
 * {@code ALEGRA_API_TOKEN_SANDBOX} existe (nunca en CI). Usa el company.id de {@code ALEGRA_E2E_COMPANY_ID}, una
 * empresa de pruebas con NIT 900559088, y un bloque de numeros al azar del rango SETP compartido por todos los
 * que prueban Alegra (un numero fijo choca con la regla 90 de la DIAN).
 */
@EnabledIfEnvironmentVariable(named = "ALEGRA_API_TOKEN_SANDBOX", matches = ".+")
// El perfil de test reemplaza application.properties: el token se toma de la variable de entorno a mano.
@SpringBootTest(properties = {"billing.provider=alegra", "alegra.token.sandbox=${ALEGRA_API_TOKEN_SANDBOX}"})
class AlegraSandboxInvoiceE2ETest {

    @Autowired
    private AlegraInvoiceClient invoiceClient;
    @Autowired
    private StoreBillingConfigRepository configRepository;
    @Autowired
    private NumberingRangeRepository rangeRepository;
    @Autowired
    private DocumentNumberAllocationRepository allocationRepository;

    private final Long storeId = 900_000L + (System.nanoTime() % 1_000_000L);

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        allocationRepository.deleteAll(allocationRepository.findAll().stream().filter(a -> a.getStoreId().equals(storeId)).toList());
        rangeRepository.deleteAll(rangeRepository.findAll().stream().filter(r -> r.getStoreId().equals(storeId)).toList());
        configRepository.findById(storeId).ifPresent(configRepository::delete);
    }

    @Test
    void aSaleBecomesAnAcceptedInvoiceWithACufeAndTheNumberWeAssigned() {
        String companyId = System.getenv().getOrDefault("ALEGRA_E2E_COMPANY_ID", "01M6KH7R1QZQK687BNMX5BBXZC");
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(storeId);
        config.setEnvironment(BillingEnvironment.SANDBOX);
        config.setStatus(BillingActivationStatus.ACTIVE);
        config.assignCompanyId(BillingEnvironment.SANDBOX, companyId);
        configRepository.save(config);

        long first = 991_000_000L + ThreadLocalRandom.current().nextLong(3_000_000L);
        rangeRepository.save(new NumberingRange(storeId, BillingEnvironment.SANDBOX, "01", "SETP", "18760000001",
                first, first + 50, LocalDate.of(2019, 1, 19), LocalDate.of(2030, 1, 19),
                "fc8eac422eba16e22ffd8c6f94b3f40a6e38162c"));

        CustomerBillingData customer = new CustomerBillingData("13", "222222222222", "", "2", "ZZ", List.of("R-99-PN"),
                "Consumidor Final", null, null, "consumidorfinal@tienda.com", null, null, "CO", "11001");
        InvoiceItemData gravado = new InvoiceItemData("REF-PROD", "Producto gravado", new BigDecimal("2"),
                new BigDecimal("8403.36"), new BigDecimal("19"), new BigDecimal("16806.72"), "94", "999", "01",
                BigDecimal.ZERO, false);
        InvoiceItemData exento = new InvoiceItemData("REF-EXENTO", "Producto exento", new BigDecimal("1"),
                new BigDecimal("5000"), BigDecimal.ZERO, new BigDecimal("5000"), "94", "999", "01", BigDecimal.ZERO, true);
        InvoiceRequest request = new InvoiceRequest(System.nanoTime() % 1_000_000L, storeId, null, "E2E-" + first, "1", "10",
                customer, List.of(gravado, exento), new BigDecimal("26000"), "Prueba E2E del POS");

        TenantContext.setStoreId(storeId);
        InvoiceResult result = invoiceClient.emitInvoice(request);

        System.out.println("E2E Alegra -> status=" + result.status() + " number=" + result.legalInvoiceNumber()
                + " providerId=" + result.providerDocumentId() + " message=" + result.errorMessage());
        assertTrue(result.isSuccess(), "esperaba una factura aceptada y fue " + result.status() + ": " + result.errorMessage());
        assertEquals("SETP" + first, result.legalInvoiceNumber());
        assertNotNull(result.cufe());
        assertNotNull(result.providerDocumentId());

        // El PDF viene dentro de files.zip: se descarga con un enlace fresco y debe ser un PDF de verdad.
        byte[] pdf = invoiceClient.downloadInvoicePDF(result.providerDocumentId());
        assertTrue(pdf.length > 1000, "el PDF no puede estar vacio");
        assertEquals("%PDF-", new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1));
        System.out.println("E2E Alegra PDF -> " + pdf.length + " bytes");

        byte[] xml = invoiceClient.downloadInvoiceXML(result.providerDocumentId());
        assertTrue(xml.length > 1000, "el XML no puede estar vacio");
        assertEquals("<?xml", new String(xml, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1));

        // Opcional: con ALEGRA_E2E_EMAIL definido se envia el correo de verdad (la direccion nunca va en el codigo).
        String recipient = System.getenv("ALEGRA_E2E_EMAIL");
        if (recipient != null && !recipient.isBlank()) {
            assertTrue(invoiceClient.sendInvoiceEmail(result.providerDocumentId(), recipient.trim()));
            System.out.println("E2E Alegra email -> enviado");
        }
    }
}
