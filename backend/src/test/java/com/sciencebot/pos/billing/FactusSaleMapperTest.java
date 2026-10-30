package com.sciencebot.pos.billing;


import com.sciencebot.pos.billing.internal.adapters.dto.CustomerBillingData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusSaleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FactusSaleMapperTest {

    private FactusSaleMapper saleMapper;

    @BeforeEach
    void setUp() {
        saleMapper = new FactusSaleMapper();
    }

    @Test
    @SuppressWarnings("unchecked")
    void saleMapper_TransformsInvoiceRequest_StandardFactusV2Payload() {
        CustomerBillingData customer = new CustomerBillingData("123456789", "1", "Empresa XYZ", "info@xyz.com", 1, 1, 11001);
        InvoiceItemData item = new InvoiceItemData("REF-01", "Laptop", 2, BigDecimal.valueOf(1500.0), BigDecimal.ZERO, BigDecimal.valueOf(3000.0));
        InvoiceRequest request = new InvoiceRequest(10L, "FACT-000010", "10", customer, List.of(item), BigDecimal.valueOf(3000.0));

        Map<String, Object> payload = saleMapper.toFactusRequestPayload(request, 8);

        assertNotNull(payload);
        assertEquals("01", payload.get("document"));
        assertEquals("10", payload.get("operation_type"));
        assertEquals(8, payload.get("numbering_range_id"));
        assertEquals("0.00", payload.get("cash_rounding_amount"));

        // Validar payment_details
        assertTrue(payload.containsKey("payment_details"));
        List<Map<String, Object>> paymentDetails = (List<Map<String, Object>>) payload.get("payment_details");
        assertEquals(1, paymentDetails.size());
        assertEquals("1", paymentDetails.get(0).get("payment_form"));
        assertEquals("10", paymentDetails.get(0).get("payment_method_code"));
        assertEquals("3000.00", paymentDetails.get(0).get("amount"));

        // Validar customer (Persona Jurídica)
        assertTrue(payload.containsKey("customer"));
        Map<String, Object> customerMap = (Map<String, Object>) payload.get("customer");
        assertEquals("123456789", customerMap.get("identification"));
        assertEquals("1", customerMap.get("dv"));
        assertEquals("1", customerMap.get("legal_organization_code"));
        assertEquals("Empresa XYZ", customerMap.get("company"));
        assertEquals("info@xyz.com", customerMap.get("email"));
        assertEquals("CO", customerMap.get("country_code"));
        assertEquals("11001", customerMap.get("municipality_code"));

        // Validar items
        assertTrue(payload.containsKey("items"));
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        assertEquals(1, items.size());
        Map<String, Object> itemMap = items.get(0);
        assertEquals("REF-01", itemMap.get("code_reference"));
        assertEquals("Laptop", itemMap.get("name"));
        assertEquals("2.00", itemMap.get("quantity"));
        assertEquals("1500.00", itemMap.get("price"));
        assertEquals("0.00", itemMap.get("discount_rate"));
        assertEquals("94", itemMap.get("unit_measure_code"));
        assertEquals("999", itemMap.get("standard_code"));

        // Validar taxes dentro del item
        assertTrue(itemMap.containsKey("taxes"));
        List<Map<String, Object>> taxes = (List<Map<String, Object>>) itemMap.get("taxes");
        assertEquals(1, taxes.size());
        assertEquals("01", taxes.get(0).get("code"));
        assertEquals("0.00", taxes.get(0).get("rate"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saleMapper_TransformsNaturalPersonCustomer_SetsNamesNotCompany() {
        CustomerBillingData customer = new CustomerBillingData(
                "13",
                "1020304050",
                "",
                "2",
                "ZZ",
                List.of("R-99-PN"),
                "Carlos Perez",
                null,
                null,
                "carlos@email.com",
                "3001234567",
                "Calle 10 # 5-20",
                "CO",
                "11001"
        );
        InvoiceItemData item = new InvoiceItemData("PROD-1", "Servicio", 1, BigDecimal.valueOf(100.0), BigDecimal.ZERO, BigDecimal.valueOf(100.0));
        InvoiceRequest request = new InvoiceRequest(20L, "FACT-000020", "10", customer, List.of(item), BigDecimal.valueOf(100.0));

        Map<String, Object> payload = saleMapper.toFactusRequestPayload(request, 8);
        Map<String, Object> customerMap = (Map<String, Object>) payload.get("customer");

        assertEquals("13", customerMap.get("identification_document_code"));
        assertEquals("1020304050", customerMap.get("identification"));
        assertEquals("2", customerMap.get("legal_organization_code"));
        assertEquals("Carlos Perez", customerMap.get("names"));
        assertNull(customerMap.get("company"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saleMapper_CreditInvoice_SendsPaymentFormTwoAndTheDueDate() {
        InvoiceRequest request = new InvoiceRequest(40L, null, null, "FACT-000040", "2", "1", null, List.of(),
                BigDecimal.valueOf(500.0), null, java.time.LocalDate.of(2026, 12, 15));

        Map<String, Object> payload = saleMapper.toFactusRequestPayload(request, 8);

        assertEquals("2026-12-15", payload.get("payment_due_date"));
        Map<String, Object> payment = ((List<Map<String, Object>>) payload.get("payment_details")).get(0);
        assertEquals("2", payment.get("payment_form"));
    }

    @Test
    void saleMapper_CashInvoice_DoesNotSendADueDate() {
        InvoiceRequest request = new InvoiceRequest(41L, "FACT-000041", "10", null, List.of(), BigDecimal.TEN);

        assertNull(saleMapper.toFactusRequestPayload(request, 8).get("payment_due_date"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saleMapper_DefaultGenericCustomer_MapsCorrectly() {
        InvoiceRequest request = new InvoiceRequest(30L, "FACT-000030", "10", null, List.of(), BigDecimal.ZERO);
        Map<String, Object> payload = saleMapper.toFactusRequestPayload(request, 8);

        Map<String, Object> customerMap = (Map<String, Object>) payload.get("customer");
        assertEquals("13", customerMap.get("identification_document_code"));
        assertEquals("222222222222", customerMap.get("identification"));
        assertEquals("2", customerMap.get("legal_organization_code"));
        assertEquals("Consumidor Final", customerMap.get("names"));
        assertEquals("consumidorfinal@tienda.com", customerMap.get("email"));
    }
}
