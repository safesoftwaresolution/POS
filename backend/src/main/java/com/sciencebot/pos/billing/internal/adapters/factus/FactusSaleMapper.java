package com.sciencebot.pos.billing.internal.adapters.factus;

import com.sciencebot.pos.billing.internal.adapters.dto.CustomerBillingData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class FactusSaleMapper {

    /**
     * Transforma una solicitud canónica de factura en el payload JSON esperado por Factus API v2.
     * Basado en la especificación estándar (tipo 01, operation_type 10).
     */
    public Map<String, Object> toFactusRequestPayload(InvoiceRequest request, int numberingRangeId) {
        Map<String, Object> payload = new LinkedHashMap<>();

        // 1. Datos generales de nivel raíz
        String referenceCode = (request.invoiceNumber() != null ? request.invoiceNumber() : "FACT") + "-" + System.currentTimeMillis();
        payload.put("reference_code", referenceCode);
        payload.put("document", "01"); // 01 = Factura electrónica de Venta
        payload.put("operation_type", "10"); // 10 = Estándar
        if (numberingRangeId > 0) {
            payload.put("numbering_range_id", numberingRangeId);
        }
        if (request.observation() != null && !request.observation().isBlank()) {
            payload.put("observation", request.observation().length() > 250
                    ? request.observation().substring(0, 250)
                    : request.observation());
        }

        // 2. Medios de pago (payment_details)
        payload.put("payment_details", mapPaymentDetails(request));
        payload.put("cash_rounding_amount", "0.00");

        // 3. Cliente (customer)
        payload.put("customer", mapCustomer(request.customer()));

        // 4. Ítems facturados (items)
        payload.put("items", mapItems(request.items()));

        return payload;
    }

    private List<Map<String, Object>> mapPaymentDetails(InvoiceRequest request) {
        List<Map<String, Object>> paymentDetails = new ArrayList<>();
        Map<String, Object> payment = new LinkedHashMap<>();

        String paymentForm = (request.paymentForm() != null && !request.paymentForm().isBlank())
                ? request.paymentForm()
                : "1"; // 1 = Contado
        String paymentMethodCode = (request.paymentMethodCode() != null && !request.paymentMethodCode().isBlank())
                ? request.paymentMethodCode()
                : "10"; // 10 = Efectivo

        payment.put("payment_form", paymentForm);
        payment.put("payment_method_code", paymentMethodCode);
        payment.put("reference_code", "pago-" + (request.saleId() != null ? request.saleId() : System.currentTimeMillis()));

        BigDecimal total = (request.totalAmount() != null) ? request.totalAmount() : BigDecimal.ZERO;
        payment.put("amount", String.format(Locale.US, "%.2f", total));

        paymentDetails.add(payment);
        return paymentDetails;
    }

    private Map<String, Object> mapCustomer(CustomerBillingData customer) {
        Map<String, Object> customerMap = new LinkedHashMap<>();

        if (customer != null) {
            String docCode = (customer.identificationDocumentCode() != null && !customer.identificationDocumentCode().isBlank())
                    ? customer.identificationDocumentCode()
                    : "13";
            customerMap.put("identification_document_code", docCode);

            String rawId = customer.identification() != null ? customer.identification().trim() : "222222222222";
            String cleanId = rawId.replaceAll("[^0-9a-zA-Z]", "");
            customerMap.put("identification", cleanId);

            if (customer.dv() != null && !customer.dv().isBlank()) {
                customerMap.put("dv", customer.dv().trim());
            }

            String orgCode = (customer.legalOrganizationCode() != null && !customer.legalOrganizationCode().isBlank())
                    ? customer.legalOrganizationCode()
                    : "2";
            customerMap.put("legal_organization_code", orgCode);

            customerMap.put("tribute_code", (customer.tributeCode() != null && !customer.tributeCode().isBlank())
                    ? customer.tributeCode()
                    : "ZZ");

            customerMap.put("responsibilities", (customer.responsibilities() != null && !customer.responsibilities().isEmpty())
                    ? customer.responsibilities()
                    : List.of("R-99-PN"));

            if ("1".equals(orgCode)) {
                String company = customer.company() != null ? customer.company() : (customer.names() != null ? customer.names() : "Empresa");
                customerMap.put("company", company);
                if (customer.tradeName() != null && !customer.tradeName().isBlank()) {
                    customerMap.put("trade_name", customer.tradeName());
                }
            } else {
                String names = customer.names() != null ? customer.names() : (customer.company() != null ? customer.company() : "Consumidor Final");
                customerMap.put("names", names);
            }

            if (customer.address() != null && !customer.address().isBlank()) {
                customerMap.put("address", customer.address());
            }
            customerMap.put("email", (customer.email() != null && !customer.email().isBlank())
                    ? customer.email()
                    : "cliente@tienda.com");
            if (customer.phone() != null && !customer.phone().isBlank()) {
                customerMap.put("phone", customer.phone());
            }
            customerMap.put("country_code", (customer.countryCode() != null && !customer.countryCode().isBlank())
                    ? customer.countryCode()
                    : "CO");
            if (customer.municipalityCode() != null && !customer.municipalityCode().isBlank()) {
                customerMap.put("municipality_code", customer.municipalityCode());
            }
        } else {
            customerMap.put("identification_document_code", "13");
            customerMap.put("identification", "222222222222");
            customerMap.put("legal_organization_code", "2");
            customerMap.put("tribute_code", "ZZ");
            customerMap.put("responsibilities", List.of("R-99-PN"));
            customerMap.put("names", "Consumidor Final");
            customerMap.put("email", "consumidorfinal@tienda.com");
            customerMap.put("country_code", "CO");
        }

        return customerMap;
    }

    private List<Map<String, Object>> mapItems(List<InvoiceItemData> items) {
        List<Map<String, Object>> itemsList = new ArrayList<>();
        if (items != null) {
            for (InvoiceItemData item : items) {
                itemsList.add(mapSingleItem(item));
            }
        }
        return itemsList;
    }

    private Map<String, Object> mapSingleItem(InvoiceItemData item) {
        Map<String, Object> itemMap = new LinkedHashMap<>();

        String cleanName = item.productName() != null ? item.productName().replaceAll("[^a-zA-Z0-9]", "") : "PROD";
        String codeRef = (item.productCode() != null && !item.productCode().isBlank())
                ? item.productCode()
                : "REF-" + cleanName + "-" + System.currentTimeMillis();

        itemMap.put("code_reference", codeRef);
        itemMap.put("name", item.productName() != null ? item.productName() : "Producto");

        BigDecimal quantity = item.quantity() != null ? item.quantity() : BigDecimal.ONE;
        itemMap.put("quantity", String.format(Locale.US, "%.2f", quantity));

        BigDecimal discountRate = item.discountRate() != null ? item.discountRate() : BigDecimal.ZERO;
        itemMap.put("discount_rate", String.format(Locale.US, "%.2f", discountRate));

        BigDecimal price = item.unitPrice() != null ? item.unitPrice() : BigDecimal.ZERO;
        itemMap.put("price", String.format(Locale.US, "%.2f", price));

        itemMap.put("unit_measure_code", (item.unitMeasureCode() != null && !item.unitMeasureCode().isBlank())
                ? item.unitMeasureCode()
                : "94");
        itemMap.put("standard_code", (item.standardCode() != null && !item.standardCode().isBlank())
                ? item.standardCode()
                : "999");

        // Impuestos requeridos en Factus v2 (array taxes)
        List<Map<String, Object>> taxesList = new ArrayList<>();
        Map<String, Object> taxMap = new LinkedHashMap<>();
        taxMap.put("code", (item.taxCode() != null && !item.taxCode().isBlank()) ? item.taxCode() : "01");

        BigDecimal taxRate = item.taxRate() != null ? item.taxRate() : BigDecimal.ZERO;
        taxMap.put("rate", String.format(Locale.US, "%.2f", taxRate));

        if (item.isExcluded()) {
            taxMap.put("is_excluded", true);
        }
        taxesList.add(taxMap);

        itemMap.put("taxes", taxesList);

        return itemMap;
    }
}
