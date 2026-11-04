package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraCompany;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceMapper;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceMapper.NoteInput;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraInvoiceMapper.OriginalInvoice;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator.Allocation;
import com.sciencebot.pos.billing.internal.adapters.dto.CustomerBillingData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Cuerpo de POST /credit-notes y /debit-notes: numeracion propia sin resolucion, periodo y documento asociado. */
class AlegraNoteMapperTest {

    private AlegraInvoiceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AlegraInvoiceMapper();
    }

    private static Allocation allocation() {
        return new Allocation(1L, 2L, "NC", 700_000_123L, null, 1L, 999_999_999L, null, null, null, "key", false);
    }

    private static AlegraCompany company() {
        return new AlegraCompany("company-1", "Tienda SAS", null, "900559088", "2", "R-99-PN", 1, "31", "a@b.co", null, null, Map.of());
    }

    private static OriginalInvoice original() {
        return new OriginalInvoice(LocalDate.of(2026, 11, 4), "SETP", 993_836_500L, "cufe-abc");
    }

    private static NoteInput note(String concept, String form, InvoiceItemData... items) {
        CustomerBillingData customer = new CustomerBillingData("13", "222222222222", "", "2", "ZZ", List.of("R-99-PN"),
                "Consumidor Final", null, null, "c@c.co", null, null, "CO", "11001");
        return new NoteInput(concept, "Nota de prueba", form, "10", "2".equals(form) ? LocalDate.of(2026, 12, 1) : null,
                customer, List.of(items));
    }

    private static InvoiceItemData item(String price, String rate) {
        return new InvoiceItemData("REF-A", "Producto", BigDecimal.ONE, new BigDecimal(price), new BigDecimal(rate),
                new BigDecimal(price), "94", "999", "01", BigDecimal.ZERO, new BigDecimal(rate).signum() == 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @Test
    void usesTheOwnPrefixAndNumberAndNeverAResolution() {
        Map<String, Object> body = mapper.toAlegraNotePayload(note("2", "1", item("10000", "19")), company(), allocation(), original());

        assertEquals("NC", body.get("prefix"));
        assertEquals(700_000_123L, body.get("number"));
        assertEquals("2", body.get("conceptCode"));
        assertFalse(body.containsKey("resolution"), "las notas no llevan resolucion");
        assertFalse(body.containsKey("documentType") && !"91".equals(body.get("documentType")) && !"92".equals(body.get("documentType")));
    }

    @Test
    void theInvoicePeriodAndTheAssociatedDocumentComeFromTheOriginalInvoice() {
        Map<String, Object> body = mapper.toAlegraNotePayload(note("2", "1", item("10000", "19")), company(), allocation(), original());

        Map<String, Object> period = map(body.get("invoicePeriod"));
        assertEquals("2026-11-04", period.get("startDate"));
        assertEquals("2026-11-04", period.get("endDate"));
        List<Map<String, Object>> associated = list(body.get("associatedDocuments"));
        assertEquals(1, associated.size());
        assertEquals("2026-11-04", associated.get(0).get("date"));
        assertEquals("01", associated.get(0).get("documentType"));
        assertEquals(993_836_500L, associated.get(0).get("number"));
        assertEquals("SETP", associated.get(0).get("prefix"));
        assertEquals("cufe-abc", associated.get(0).get("uuid"));
    }

    @Test
    void itemsAndTotalsUseTheSameRoundingAsInvoices() {
        Map<String, Object> body = mapper.toAlegraNotePayload(note("1", "1", item("10000", "19"), item("500", "0")),
                company(), allocation(), original());

        Map<String, Object> totals = map(body.get("totalAmounts"));
        assertEquals(0, new BigDecimal("10500.00").compareTo((BigDecimal) totals.get("grossTotal")));
        assertEquals(0, new BigDecimal("1900.00").compareTo((BigDecimal) totals.get("taxTotal")));
        assertEquals(0, new BigDecimal("12400.00").compareTo((BigDecimal) totals.get("payableTotal")));
        assertEquals("COP", totals.get("currencyCode"));
        List<Map<String, Object>> lines = list(body.get("items"));
        assertEquals(2, lines.size());
        assertEquals("19.00", list(lines.get(0).get("taxes")).get(0).get("taxPercentage"));
    }

    @Test
    void companyCustomerPaymentAndNoteArePresent() {
        Map<String, Object> body = mapper.toAlegraNotePayload(note("1", "2", item("100", "19")), company(), allocation(), original());

        assertEquals("company-1", map(body.get("company")).get("id"));
        assertEquals("31", map(body.get("company")).get("identificationType"));
        assertEquals("13", map(body.get("customer")).get("identificationType"));
        assertEquals("c@c.co", map(body.get("customer")).get("email"));
        Map<String, Object> payment = list(body.get("payments")).get(0);
        assertEquals("2", payment.get("paymentForm"));
        assertEquals("2026-12-01", payment.get("paymentDueDate"));
        assertEquals(List.of("Nota de prueba"), body.get("note"));
    }

    @Test
    void aNoteWithoutItemsOrAnOriginalInvoiceIsRefused() {
        assertThrows(IllegalStateException.class, () -> mapper.toAlegraNotePayload(note("1", "1"), company(), allocation(), original()));
        assertThrows(IllegalStateException.class, () -> mapper.toAlegraNotePayload(
                note("1", "1", item("100", "19")), company(), allocation(), null));
    }
}
