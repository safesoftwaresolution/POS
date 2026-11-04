package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraCompany;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceMapper;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator.Allocation;
import com.sciencebot.pos.billing.internal.adapters.dto.CustomerBillingData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Arma el cuerpo de POST /invoices de Alegra: totales de las lineas ya redondeadas, resolucion y company completos. */
class AlegraInvoiceMapperTest {

    private AlegraInvoiceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AlegraInvoiceMapper();
    }

    private static Allocation allocation() {
        return new Allocation(1L, 2L, "SETP", 990_000_101L, "18760000001", 990_000_000L, 995_000_000L,
                LocalDate.of(2019, 1, 19), LocalDate.of(2030, 1, 19), "technical-key", "key-uuid", false);
    }

    private static AlegraCompany company(Integer organizationType, String identificationType) {
        return new AlegraCompany("company-1", "Soluciones de Prueba SAS", null, "900559088", "2", "R-99-PN",
                organizationType, identificationType, "a@b.co", null,
                new AlegraCompany.Address("Cra. 13 #12-12", "11001", "11", "CO"), Map.of("invoices", "AUTHORIZED"));
    }

    private static CustomerBillingData consumerFinal() {
        return new CustomerBillingData("13", "222222222222", "", "2", "ZZ", List.of("R-99-PN"), "Consumidor Final",
                null, null, "consumidorfinal@tienda.com", null, null, "CO", "11001");
    }

    private static InvoiceItemData item(String name, String qty, String price, String rate) {
        BigDecimal quantity = new BigDecimal(qty);
        BigDecimal unitPrice = new BigDecimal(price);
        return new InvoiceItemData("REF-" + name, name, quantity, unitPrice, new BigDecimal(rate),
                unitPrice.multiply(quantity), "94", "999", "01", BigDecimal.ZERO, new BigDecimal(rate).signum() == 0);
    }

    private static InvoiceRequest request(CustomerBillingData customer, String paymentForm, InvoiceItemData... items) {
        return new InvoiceRequest(7L, 5L, null, "FAC-1", paymentForm, "10", customer, List.of(items),
                BigDecimal.ZERO, "Factura emitida desde Sistema POS",
                "2".equals(paymentForm) ? LocalDate.of(2026, 12, 1) : null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static void assertMoney(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(String.valueOf(actual))),
                "esperaba " + expected + " y fue " + actual);
    }

    @Test
    void standardInvoiceWith19PercentVat() {
        Map<String, Object> body = mapper.toAlegraPayload(
                request(consumerFinal(), "1", item("Botella", "1", "10000", "19")), company(1, "31"), allocation());

        assertEquals("01", body.get("documentType"));
        assertEquals(990_000_101L, body.get("number"));
        Map<String, Object> line = list(body.get("items")).get(0);
        assertMoney("10000", line.get("subtotal"));
        assertMoney("1900", line.get("taxAmount"));
        Map<String, Object> tax = list(line.get("taxes")).get(0);
        assertEquals("01", tax.get("taxCode"));
        assertEquals("19.00", tax.get("taxPercentage"));
        assertMoney("1900", tax.get("taxAmount"));
        assertMoney("10000", tax.get("taxableAmount"));
        Map<String, Object> totals = map(body.get("totalAmounts"));
        assertMoney("10000", totals.get("grossTotal"));
        assertMoney("10000", totals.get("taxableTotal"));
        assertMoney("1900", totals.get("taxTotal"));
        assertMoney("11900", totals.get("payableTotal"));
        assertMoney("0", totals.get("discountTotal"));
        assertEquals("COP", totals.get("currencyCode"));
    }

    @Test
    void resolutionComesFromTheAssignedRange() {
        Map<String, Object> resolution = map(mapper.toAlegraPayload(
                request(consumerFinal(), "1", item("A", "1", "100", "19")), company(1, "31"), allocation()).get("resolution"));

        assertEquals("18760000001", resolution.get("resolutionNumber"));
        assertEquals("SETP", resolution.get("prefix"));
        assertEquals(990_000_000L, resolution.get("minNumber"));
        assertEquals(995_000_000L, resolution.get("maxNumber"));
        assertEquals("2019-01-19", resolution.get("startDate"));
        assertEquals("2030-01-19", resolution.get("endDate"));
        assertEquals("technical-key", resolution.get("technicalKey"));
    }

    @Test
    void totalsAreTheSumOfTheAlreadyRoundedLines() {
        // 3 x 3333.33 = 9999.99 -> IVA 1899.9981 = 1900.00 ; 1 x 0.05 -> IVA 0.0095 = 0.01 ; 2 x 0.04 -> 0.08 -> IVA 0.0152 = 0.02
        Map<String, Object> body = mapper.toAlegraPayload(request(consumerFinal(), "1",
                item("A", "3", "3333.33", "19"), item("B", "1", "0.05", "19"), item("C", "2", "0.04", "19")),
                company(1, "31"), allocation());

        List<Map<String, Object>> lines = list(body.get("items"));
        assertMoney("9999.99", lines.get(0).get("subtotal"));
        assertMoney("1900.00", lines.get(0).get("taxAmount"));
        assertMoney("0.05", lines.get(1).get("subtotal"));
        assertMoney("0.01", lines.get(1).get("taxAmount"));
        assertMoney("0.08", lines.get(2).get("subtotal"));
        assertMoney("0.02", lines.get(2).get("taxAmount"));
        Map<String, Object> totals = map(body.get("totalAmounts"));
        assertMoney("10000.12", totals.get("grossTotal"));
        assertMoney("1900.03", totals.get("taxTotal"));
        assertMoney("11900.15", totals.get("payableTotal"));
    }

    @Test
    void exemptItemCarriesZeroTaxAndMixedInvoicesSumCorrectly() {
        Map<String, Object> body = mapper.toAlegraPayload(request(consumerFinal(), "1",
                item("Gravado", "1", "1000", "19"), item("Exento", "2", "500", "0")), company(1, "31"), allocation());

        Map<String, Object> exempt = list(body.get("items")).get(1);
        assertMoney("1000", exempt.get("subtotal"));
        assertMoney("0", exempt.get("taxAmount"));
        Map<String, Object> exemptTax = list(exempt.get("taxes")).get(0);
        assertEquals("0.00", exemptTax.get("taxPercentage"));
        assertMoney("0", exemptTax.get("taxAmount"));
        Map<String, Object> totals = map(body.get("totalAmounts"));
        assertMoney("2000", totals.get("grossTotal"));
        assertMoney("190", totals.get("taxTotal"));
        assertMoney("2190", totals.get("payableTotal"));
    }

    @Test
    void discountRateReducesTheLineSubtotalBeforeTax() {
        InvoiceItemData discounted = new InvoiceItemData("REF-D", "Con descuento", new BigDecimal("2"),
                new BigDecimal("1000"), new BigDecimal("19"), new BigDecimal("2000"), "94", "999", "01",
                new BigDecimal("10"), false);

        Map<String, Object> line = list(mapper.toAlegraPayload(request(consumerFinal(), "1", discounted),
                company(1, "31"), allocation()).get("items")).get(0);

        assertMoney("1800", line.get("subtotal"));
        assertMoney("342", line.get("taxAmount"));
    }

    @Test
    void consumerFinalAndACompanyWithNitAndCheckDigit() {
        Map<String, Object> consumer = map(mapper.toAlegraPayload(
                request(consumerFinal(), "1", item("A", "1", "100", "19")), company(1, "31"), allocation()).get("customer"));
        assertEquals("13", consumer.get("identificationType"));
        assertEquals("222222222222", consumer.get("identificationNumber"));
        assertEquals("Consumidor Final", consumer.get("name"));

        CustomerBillingData legal = new CustomerBillingData("31", "900123456", "7", "1", "01",
                List.of("O-13", "R-99-PN"), null, "Cliente SAS", null, "c@c.co", null, "Calle 1", "CO", "11001");
        Map<String, Object> withNit = map(mapper.toAlegraPayload(
                request(legal, "1", item("A", "1", "100", "19")), company(1, "31"), allocation()).get("customer"));
        assertEquals("31", withNit.get("identificationType"));
        assertEquals("900123456", withNit.get("identificationNumber"));
        assertEquals("7", withNit.get("dv"));
        assertEquals("Cliente SAS", withNit.get("name"));
        assertEquals("c@c.co", withNit.get("email"));
    }

    @Test
    void companyBlockIsCompleteAndDefaultsTheTwoAttributesAlegraRequiresWhenTheCompanyLacksThem() {
        Map<String, Object> filled = map(mapper.toAlegraPayload(request(consumerFinal(), "1", item("A", "1", "100", "19")),
                company(2, "13"), allocation()).get("company"));
        assertEquals("company-1", filled.get("id"));
        assertEquals(2, filled.get("organizationType"));
        assertEquals("13", filled.get("identificationType"));
        assertEquals("900559088", filled.get("identificationNumber"));
        assertEquals("2", filled.get("dv"));
        assertEquals("Soluciones de Prueba SAS", filled.get("name"));
        assertEquals("R-99-PN", filled.get("regimeCode"));
        assertEquals("a@b.co", filled.get("email"));
        assertEquals("11001", map(filled.get("address")).get("city"));

        Map<String, Object> defaults = map(mapper.toAlegraPayload(request(consumerFinal(), "1", item("A", "1", "100", "19")),
                company(null, null), allocation()).get("company"));
        assertEquals("31", defaults.get("identificationType"), "una empresa con NIT");
        assertEquals(1, defaults.get("organizationType"));

        AlegraCompany bare = new AlegraCompany("company-2", "Solo nombre", null, "900", "1", null, null, null, null, null, null, Map.of());
        Map<String, Object> minimal = map(mapper.toAlegraPayload(request(consumerFinal(), "1", item("A", "1", "100", "19")),
                bare, allocation()).get("company"));
        assertEquals("R-99-PN", minimal.get("regimeCode"), "Alegra la exige aunque la empresa no la tenga guardada");
        assertFalse(minimal.containsKey("address"));
    }

    @Test
    void creditSalesCarryTheDueDateAndCashSalesDoNot() {
        Map<String, Object> credit = list(mapper.toAlegraPayload(request(consumerFinal(), "2", item("A", "1", "100", "19")),
                company(1, "31"), allocation()).get("payments")).get(0);
        assertEquals("2", credit.get("paymentForm"));
        assertEquals("2026-12-01", credit.get("paymentDueDate"));

        Map<String, Object> cash = list(mapper.toAlegraPayload(request(consumerFinal(), "1", item("A", "1", "100", "19")),
                company(1, "31"), allocation()).get("payments")).get(0);
        assertEquals("1", cash.get("paymentForm"));
        assertEquals("10", cash.get("paymentMethod"));
        assertFalse(cash.containsKey("paymentDueDate"));
    }

    @Test
    void itemsIdentifyTheGoodWithAUnspscCodeOrTheTaxpayersOwnCode() {
        InvoiceItemData unspsc = new InvoiceItemData("REF-U", "Con UNSPSC", BigDecimal.ONE, new BigDecimal("100"),
                new BigDecimal("19"), new BigDecimal("100"), "94", "50202300", "01", BigDecimal.ZERO, false);
        InvoiceItemData own = item("Propio", "1", "100", "19");

        List<Map<String, Object>> lines = list(mapper.toAlegraPayload(request(consumerFinal(), "1", unspsc, own),
                company(1, "31"), allocation()).get("items"));

        Map<String, Object> standard = map(lines.get(0).get("standardCode"));
        assertEquals("50202300", standard.get("identificationId"));
        assertEquals("001", standard.get("id"));
        Map<String, Object> ownCode = map(lines.get(1).get("standardCode"));
        assertEquals("REF-Propio", ownCode.get("identificationId"));
        assertEquals("999", ownCode.get("id"));
        assertEquals("94", lines.get(0).get("unitCode"));
        assertEquals("Con UNSPSC", lines.get(0).get("description"));
    }

    @Test
    void observationBecomesANote() {
        Map<String, Object> body = mapper.toAlegraPayload(request(consumerFinal(), "1", item("A", "1", "100", "19")),
                company(1, "31"), allocation());

        assertEquals(List.of("Factura emitida desde Sistema POS"), body.get("note"));
    }

    @Test
    void aRangeWithoutDatesOrTechnicalKeyIsRefusedWithAClearMessage() {
        Allocation incomplete = new Allocation(1L, 2L, "SETP", 1L, "1876", 1L, 10L, null, null, null, "k", false);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> mapper.toAlegraPayload(
                request(consumerFinal(), "1", item("A", "1", "100", "19")), company(1, "31"), incomplete));

        assertTrue(error.getMessage().contains("vigencia") && error.getMessage().contains("clave técnica"), error.getMessage());
    }
}
