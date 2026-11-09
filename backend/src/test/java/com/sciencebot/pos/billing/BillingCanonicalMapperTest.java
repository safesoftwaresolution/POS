package com.sciencebot.pos.billing;


import com.sciencebot.pos.billing.internal.adapters.dto.CustomerBillingData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraCompany;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceMapper;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator.Allocation;
import com.sciencebot.pos.billing.internal.mappers.BillingCanonicalMapper;
import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleItemDto;
import com.sciencebot.pos.sales.SaleItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BillingCanonicalMapperTest {

    @Mock
    private CustomerFacade customerFacade;

    @Mock
    private ProductFacade productFacade;


    @Mock
    private com.sciencebot.pos.credit.CreditFacade creditFacade;

    private BillingCanonicalMapper mapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mapper = new BillingCanonicalMapper(customerFacade, productFacade, creditFacade);
    }

    private SaleDto saleWith(String paymentMethod) {
        return new SaleDto(1L, 5L, null, "FACT-0001", LocalDateTime.now(), "Cliente",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(1000), BigDecimal.ZERO,
                "cajero", List.of(), paymentMethod);
    }

    @Test
    void toInvoiceRequest_CreditSale_IsIssuedOnCreditWithTheAgreedDueDate() {
        java.time.LocalDate due = java.time.LocalDate.of(2026, 12, 15);
        when(creditFacade.findChargeDueDate(5L, 1L)).thenReturn(Optional.of(due));

        InvoiceRequest request = mapper.toInvoiceRequest(saleWith("CREDIT"));

        assertEquals("2", request.paymentForm());
        assertEquals(due, request.paymentDueDate());
        assertEquals("1", request.paymentMethodCode());
    }

    @Test
    void toInvoiceRequest_NonCreditSale_IsCashAndNeverAsksCredit() {
        InvoiceRequest request = mapper.toInvoiceRequest(saleWith("CASH"));

        assertEquals("1", request.paymentForm());
        assertNull(request.paymentDueDate());
        verifyNoInteractions(creditFacade);
    }

    @Test
    void toInvoiceRequest_MunicipalityStoredWithoutItsLeadingZero_IsSentAsTheFiveDigitDaneCode() {
        Long customerId = 16L;
        CustomerDto medellin = new CustomerDto(customerId, "Ana Ruiz", "1020304051", "ana@example.com", "3001234568",
                "Calle 10 # 40-20", true, "", 2, 21, 5001); // 05001 Medellín: la columna es entera y pierde el cero
        when(customerFacade.getById(customerId)).thenReturn(Optional.of(medellin));
        SaleItemDto item = new SaleItemDto(1L, 100L, "Camiseta", 1, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        SaleDto sale = new SaleDto(1L, 5L, customerId, "FACT-0002", LocalDateTime.now(), "Ana Ruiz",
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "admin", List.of(item), "CASH");

        assertEquals("05001", mapper.toInvoiceRequest(sale).customer().municipalityCode());
    }

    @Test
    void toInvoiceRequest_WithNaturalPersonCustomer_ResolvesNaturalPersonCorrectly() {
        Long customerId = 15L;
        CustomerDto customer = new CustomerDto(
                customerId,
                "Carlos Gómez",
                "1020304050",
                "carlos@example.com",
                "3001234567",
                "Calle 100 # 20-30",
                true,
                "",
                2, // Persona Natural
                21, // No responsable de IVA
                11001
        );
        when(customerFacade.getById(customerId)).thenReturn(Optional.of(customer));

        SaleItemDto item = new SaleItemDto(1L, 100L, "Camiseta Polo", 2, BigDecimal.valueOf(50000.00), BigDecimal.valueOf(100000.00), BigDecimal.valueOf(19.00), BigDecimal.valueOf(15966.38));
        SaleDto sale = new SaleDto(
                1L, 5L, customerId, "FACT-0001", LocalDateTime.now(), "Carlos Gómez",
                BigDecimal.valueOf(100000.00), BigDecimal.valueOf(100000.00), BigDecimal.ZERO,
                "carlos.admin", List.of(item), "CARD"
        );

        InvoiceRequest request = mapper.toInvoiceRequest(sale);

        assertNotNull(request);
        assertEquals(5L, request.storeId());
        assertNull(request.numberingRangeId(), "el rango lo decide el adaptador con la configuración del local");
        assertEquals("48", request.paymentMethodCode()); // CARD -> 48

        CustomerBillingData custData = request.customer();
        assertNotNull(custData);
        assertEquals("13", custData.identificationDocumentCode()); // Persona Natural -> 13
        assertEquals("1020304050", custData.identification());
        assertEquals("2", custData.legalOrganizationCode()); // Natural
        assertEquals("Carlos Gómez", custData.names());
        assertNull(custData.company());
        assertEquals("carlos@example.com", custData.email());
        assertEquals("11001", custData.municipalityCode());

        // Check item taxes
        assertEquals(1, request.items().size());
        InvoiceItemData itemData = request.items().get(0);
        assertEquals(BigDecimal.valueOf(19.0), itemData.taxRate());
        assertFalse(itemData.isExcluded());
        assertEquals("01", itemData.taxCode());
    }

    @Test
    void toInvoiceRequest_WithLegalEntityCustomer_ResolvesLegalEntityCorrectly() {
        Long customerId = 20L;
        CustomerDto customer = new CustomerDto(
                customerId,
                "Inversiones Acme SAS",
                "900123456-7",
                "facturacion@acme.com",
                "6012345678",
                "Carrera 7 # 72-10",
                true,
                "7",
                1, // Jurídica
                1, // Responsable de IVA
                11001
        );
        when(customerFacade.getById(customerId)).thenReturn(Optional.of(customer));

        SaleDto sale = new SaleDto(
                2L, 5L, customerId, "FACT-0002", LocalDateTime.now(), "Inversiones Acme SAS",
                BigDecimal.valueOf(200000.00), BigDecimal.valueOf(200000.00), BigDecimal.ZERO,
                "admin", List.of(), "TRANSFER"
        );

        InvoiceRequest request = mapper.toInvoiceRequest(sale);

        assertNotNull(request);
        assertEquals("31", request.paymentMethodCode()); // TRANSFER -> 31

        CustomerBillingData custData = request.customer();
        assertEquals("31", custData.identificationDocumentCode()); // NIT -> 31
        assertEquals("900123456", custData.identification());
        assertEquals("7", custData.dv());
        assertEquals("1", custData.legalOrganizationCode()); // Jurídica
        assertEquals("01", custData.tributeCode());
        assertEquals("Inversiones Acme SAS", custData.company());
        assertNull(custData.names());
    }

    @Test
    void toInvoiceRequest_WhenCustomerNull_DefaultsToConsumidorFinal() {
        SaleDto sale = new SaleDto(
                3L, 5L, null, "FACT-0003", LocalDateTime.now(), null,
                BigDecimal.valueOf(15000.00), BigDecimal.valueOf(15000.00), BigDecimal.ZERO,
                "admin", List.of(), "CASH"
        );

        InvoiceRequest request = mapper.toInvoiceRequest(sale);

        assertNotNull(request);
        assertEquals("10", request.paymentMethodCode()); // CASH -> 10
        CustomerBillingData custData = request.customer();
        assertEquals("222222222222", custData.identification());
        assertEquals("Consumidor Final", custData.names());
        assertEquals("13", custData.identificationDocumentCode());
    }

    @Test
    void toInvoiceRequest_PaymentMethodMapping_ResolvesAllMethods() {
        SaleDto saleNequi = new SaleDto(4L, 5L, null, "FACT-0004", LocalDateTime.now(), null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "admin", List.of(), "NEQUI");
        assertEquals("42", mapper.toInvoiceRequest(saleNequi).paymentMethodCode());

        SaleDto saleDaviplata = new SaleDto(5L, 5L, null, "FACT-0005", LocalDateTime.now(), null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "admin", List.of(), "DAVIPLATA");
        assertEquals("42", mapper.toInvoiceRequest(saleDaviplata).paymentMethodCode());

        SaleDto saleUnknown = new SaleDto(6L, 5L, null, "FACT-0006", LocalDateTime.now(), null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "admin", List.of(), "UNKNOWN");
        assertEquals("10", mapper.toInvoiceRequest(saleUnknown).paymentMethodCode());
    }

    @Test
    void toInvoiceRequest_ResolvesProductUnspscCode() {
        Long productId = 50L;
        ProductDto prod = new ProductDto(
                productId, "LAP-01", "770123456", "Laptop HP", "Tecnologia",
                null, null, BigDecimal.valueOf(2000000.00), BigDecimal.valueOf(2500000.00),
                5, 1, true, null,
                BigDecimal.valueOf(19.00), "43211503", null, null
        );
        when(productFacade.getById(productId)).thenReturn(Optional.of(prod));

        SaleItemDto item = new SaleItemDto(1L, productId, "Laptop HP", 1, BigDecimal.valueOf(2500000.00), BigDecimal.valueOf(2500000.00), BigDecimal.valueOf(19.00), BigDecimal.valueOf(399159.66));
        SaleDto sale = new SaleDto(7L, 5L, null, "FACT-0007", LocalDateTime.now(), null, BigDecimal.valueOf(2500000.00), BigDecimal.valueOf(2500000.00), BigDecimal.ZERO, "admin", List.of(item), "CASH");

        InvoiceRequest request = mapper.toInvoiceRequest(sale);
        assertEquals(1, request.items().size());
        assertEquals("43211503", request.items().get(0).standardCode());
        assertEquals("94", request.items().get(0).unitMeasureCode());
    }

    @Test
    void toInvoiceRequest_ServiceLineUsesSnapshotUnitAndUnspsc() {
        SaleItemDto item = new SaleItemDto(1L, null, "Masaje 60 min", 2, BigDecimal.valueOf(119000.00),
                BigDecimal.valueOf(238000.00), BigDecimal.valueOf(19.00), BigDecimal.valueOf(38000.00),
                SaleItemType.SERVICE, 2L, "HUR", "90111500");
        SaleDto sale = new SaleDto(8L, 5L, null, "FACT-0008", LocalDateTime.now(), null, BigDecimal.valueOf(238000.00),
                BigDecimal.valueOf(238000.00), BigDecimal.ZERO, "admin", List.of(item), "CASH");

        InvoiceItemData data = mapper.toInvoiceRequest(sale).items().get(0);

        assertEquals("HUR", data.unitMeasureCode());
        assertEquals("90111500", data.standardCode());
        assertEquals(0, data.unitPrice().compareTo(BigDecimal.valueOf(100000.00)), "precio neto sin IVA");
        verifyNoInteractions(productFacade);
    }

    @Test
    void chargedGrossIsPreservedForHighQuantityAndMixedTaxServiceLines() {
        for (String rate : List.of("0", "5", "19")) {
            SaleItemDto item = new SaleItemDto(1L, null, "Service", 100, new BigDecimal("100.00"),
                    new BigDecimal("10000.00"), new BigDecimal(rate), BigDecimal.ZERO,
                    SaleItemType.SERVICE, 2L, "HUR", "90111500");
            Map<String, Object> payload = payloadFor(List.of(item), new BigDecimal("10000.00"));
            assertPayloadAmounts("10000.00", payload);
            Map<String, Object> line = lines(payload).get(0);
            assertMoney("10000.00", ((BigDecimal) line.get("subtotal")).add((BigDecimal) line.get("taxAmount")));
        }

        List<SaleItemDto> mixed = List.of(
                new SaleItemDto(1L, null, "Taxed service", 100, new BigDecimal("100.00"),
                        new BigDecimal("10000.00"), new BigDecimal("19"), BigDecimal.ZERO,
                        SaleItemType.SERVICE, 2L, "HUR", "90111500"),
                new SaleItemDto(2L, 100L, "Reduced tax", 3, new BigDecimal("0.05"),
                        new BigDecimal("0.15"), new BigDecimal("5"), BigDecimal.ZERO),
                new SaleItemDto(3L, 101L, "Untaxed", 2, new BigDecimal("0.01"),
                        new BigDecimal("0.02"), BigDecimal.ZERO, BigDecimal.ZERO));
        assertPayloadAmounts("10000.17", payloadFor(mixed, new BigDecimal("10000.17")));
    }

    @Test
    void theStoredGrossSubtotalIsAuthoritativeRatherThanReconstructedFromUnitPrice() {
        SaleItemDto item = new SaleItemDto(1L, 100L, "Historical price snapshot", 3, new BigDecimal("33.33"),
                new BigDecimal("100.00"), new BigDecimal("19"), BigDecimal.ZERO);

        assertPayloadAmounts("100.00", payloadFor(List.of(item), new BigDecimal("100.00")));
    }

    private Map<String, Object> payloadFor(List<SaleItemDto> items, BigDecimal total) {
        SaleDto sale = new SaleDto(10L, 5L, null, "FACT-0010", LocalDateTime.now(), null, total,
                total, BigDecimal.ZERO, "admin", items, "CASH");
        AlegraCompany company = new AlegraCompany("company-1", "Test Store", null, "900559088", "2",
                "R-99-PN", 1, "31", "qa@example.com", null, null, Map.of());
        Allocation allocation = new Allocation(1L, 2L, "SETP", 990000001L, "18760000001", 990000000L,
                995000000L, LocalDate.of(2019, 1, 19), LocalDate.of(2030, 1, 19), "key", "idempotency-key", false);
        return new AlegraInvoiceMapper().toAlegraPayload(mapper.toInvoiceRequest(sale), company, allocation);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lines(Map<String, Object> payload) {
        return (List<Map<String, Object>>) payload.get("items");
    }

    @SuppressWarnings("unchecked")
    private static void assertPayloadAmounts(String expectedGross, Map<String, Object> payload) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (Map<String, Object> line : lines(payload)) {
            subtotal = subtotal.add((BigDecimal) line.get("subtotal"));
            tax = tax.add((BigDecimal) line.get("taxAmount"));
            Map<String, Object> lineTax = ((List<Map<String, Object>>) line.get("taxes")).get(0);
            assertMoney(((BigDecimal) line.get("taxAmount")).toPlainString(), lineTax.get("taxAmount"));
            assertMoney(((BigDecimal) line.get("subtotal")).toPlainString(), lineTax.get("taxableAmount"));
            BigDecimal extendedPrice = ((BigDecimal) line.get("price")).multiply((BigDecimal) line.get("quantity"));
            assertMoney(((BigDecimal) line.get("subtotal")).toPlainString(),
                    extendedPrice.setScale(2, java.math.RoundingMode.HALF_UP));
        }
        Map<String, Object> totals = (Map<String, Object>) payload.get("totalAmounts");
        assertMoney(expectedGross, subtotal.add(tax));
        assertMoney(expectedGross, totals.get("payableTotal"));
        assertMoney(subtotal.toPlainString(), totals.get("grossTotal"));
        assertMoney(tax.toPlainString(), totals.get("taxTotal"));
    }

    private static void assertMoney(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual));
    }
}
