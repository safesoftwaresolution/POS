package com.sciencebot.pos.billing.internal.adapters.alegra;

import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator.Allocation;
import com.sciencebot.pos.billing.internal.adapters.dto.CustomerBillingData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arma el cuerpo de {@code POST /invoices} de Alegra a partir de la solicitud canónica, la empresa del local y el
 * número asignado. Los importes de línea se redondean a 2 decimales (HALF_UP). Si la venta incluye su importe bruto
 * guardado, la base y el impuesto se desglosan de ese importe para conservar exactamente lo cobrado en el POS.
 */
@Component
@ConditionalOnProperty(name = "billing.provider", havingValue = "alegra")
public class AlegraInvoiceMapper {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MAX_NOTE = 5000;

    /** Lo que define una nota crédito o débito, independiente del formato de cada solicitud. */
    public record NoteInput(String conceptCode, String observation, String paymentForm, String paymentMethodCode,
                            LocalDate paymentDueDate, CustomerBillingData customer, List<InvoiceItemData> items) {}

    /** La factura aceptada que la nota referencia: su fecha, su prefijo y número legal y su CUFE. */
    public record OriginalInvoice(LocalDate date, String prefix, long number, String cufe) {}

    /**
     * Cuerpo de {@code POST /credit-notes} o {@code /debit-notes}. La numeración (prefijo y número) es la propia y no
     * lleva resolución; el periodo y el documento asociado salen de la factura de origen (Alegra los exige).
     */
    public Map<String, Object> toAlegraNotePayload(NoteInput note, AlegraCompany company, Allocation allocation,
                                                    OriginalInvoice original) {
        if (note.items() == null || note.items().isEmpty()) {
            throw new IllegalStateException("La nota no tiene ítems");
        }
        if (original == null) {
            throw new IllegalStateException("La nota necesita la factura aceptada que referencia");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prefix", allocation.prefix());
        body.put("number", allocation.number());
        body.put("conceptCode", note.conceptCode());
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("startDate", original.date().toString());
        period.put("endDate", original.date().toString());
        body.put("invoicePeriod", period);
        Map<String, Object> associated = new LinkedHashMap<>();
        associated.put("date", original.date().toString());
        associated.put("documentType", "01");
        associated.put("number", original.number());
        associated.put("prefix", original.prefix());
        associated.put("uuid", original.cufe());
        body.put("associatedDocuments", List.of(associated));
        body.put("company", mapCompany(company));
        body.put("customer", mapCustomer(note.customer()));

        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (InvoiceItemData item : note.items()) {
            Map<String, Object> line = mapItem(item);
            gross = gross.add((BigDecimal) line.get("subtotal"));
            tax = tax.add((BigDecimal) line.get("taxAmount"));
            lines.add(line);
        }
        body.put("items", lines);
        Map<String, Object> payment = new LinkedHashMap<>();
        String form = firstNonBlank(note.paymentForm(), "1");
        payment.put("paymentForm", form);
        payment.put("paymentMethod", firstNonBlank(note.paymentMethodCode(), "10"));
        if ("2".equals(form) && note.paymentDueDate() != null) {
            payment.put("paymentDueDate", note.paymentDueDate().toString());
        }
        body.put("payments", List.of(payment));
        body.put("totalAmounts", totals(gross, tax));
        if (note.observation() != null && !note.observation().isBlank()) {
            String text = note.observation().trim();
            body.put("note", List.of(text.length() > MAX_NOTE ? text.substring(0, MAX_NOTE) : text));
        }
        return body;
    }

    public Map<String, Object> toAlegraPayload(InvoiceRequest request, AlegraCompany company, Allocation allocation) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("documentType", "01");
        body.put("number", allocation.number());
        body.put("resolution", mapResolution(allocation));
        body.put("company", mapCompany(company));
        body.put("customer", mapCustomer(request.customer()));

        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (InvoiceItemData item : request.items()) {
            Map<String, Object> line = mapItem(item);
            gross = gross.add((BigDecimal) line.get("subtotal"));
            tax = tax.add((BigDecimal) line.get("taxAmount"));
            lines.add(line);
        }
        body.put("items", lines);
        body.put("payments", List.of(mapPayment(request)));
        body.put("totalAmounts", totals(gross, tax));

        if (request.observation() != null && !request.observation().isBlank()) {
            String note = request.observation().trim();
            body.put("note", List.of(note.length() > MAX_NOTE ? note.substring(0, MAX_NOTE) : note));
        }
        return body;
    }

    private static Map<String, Object> mapResolution(Allocation allocation) {
        if (allocation.resolutionNumber() == null || allocation.resolutionNumber().isBlank()
                || allocation.startDate() == null || allocation.endDate() == null
                || allocation.technicalKey() == null || allocation.technicalKey().isBlank()) {
            throw new IllegalStateException("La resolución del rango " + allocation.prefix() + " no tiene número, "
                    + "fechas de vigencia o clave técnica, y Alegra los exige en cada factura. Complétalos en el rango");
        }
        Map<String, Object> resolution = new LinkedHashMap<>();
        resolution.put("resolutionNumber", allocation.resolutionNumber().trim());
        resolution.put("prefix", allocation.prefix());
        resolution.put("minNumber", allocation.minNumber());
        resolution.put("maxNumber", allocation.maxNumber());
        resolution.put("startDate", allocation.startDate().toString());
        resolution.put("endDate", allocation.endDate().toString());
        resolution.put("technicalKey", allocation.technicalKey().trim());
        return resolution;
    }

    /**
     * El bloque company completo: con solo el id Alegra rechaza la factura si la empresa no tiene guardados el tipo
     * de organización y de identificación, ni su responsabilidad. Si la empresa no los trae se asume una empresa con
     * NIT (31, jurídica) y la responsabilidad genérica R-99-PN (No aplica). Lo correcto en producción es completar la
     * empresa en Alegra al darla de alta.
     */
    private static Map<String, Object> mapCompany(AlegraCompany company) {
        String identificationType = company.identificationType() != null ? company.identificationType() : "31";
        Integer organizationType = company.organizationType() != null
                ? company.organizationType() : ("31".equals(identificationType) ? 1 : 2);
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("id", company.id());
        block.put("organizationType", organizationType);
        block.put("identificationType", identificationType);
        putIfPresent(block, "identificationNumber", company.identification());
        putIfPresent(block, "dv", company.dv());
        putIfPresent(block, "name", company.name());
        block.put("regimeCode", firstNonBlank(company.regimeCode(), "R-99-PN"));
        putIfPresent(block, "email", company.email());
        if (company.address() != null) {
            Map<String, Object> address = new LinkedHashMap<>();
            putIfPresent(address, "address", company.address().address());
            putIfPresent(address, "city", company.address().city());
            putIfPresent(address, "department", company.address().department());
            putIfPresent(address, "country", company.address().country());
            if (!address.isEmpty()) {
                block.put("address", address);
            }
        }
        return block;
    }

    private static Map<String, Object> mapCustomer(CustomerBillingData customer) {
        Map<String, Object> block = new LinkedHashMap<>();
        if (customer == null) {
            block.put("name", "Consumidor Final");
            block.put("identificationType", "13");
            block.put("identificationNumber", "222222222222");
            return block;
        }
        String name = firstNonBlank(customer.company(), customer.names(), "Consumidor Final");
        String identification = customer.identification() != null
                ? customer.identification().trim().replaceAll("[^0-9a-zA-Z]", "") : "";
        block.put("name", name);
        block.put("identificationType", firstNonBlank(customer.identificationDocumentCode(), "13"));
        block.put("identificationNumber", identification.isEmpty() ? "222222222222" : identification);
        putIfPresent(block, "dv", customer.dv());
        putIfPresent(block, "email", customer.email());
        putIfPresent(block, "phone", customer.phone());
        return block;
    }

    private static Map<String, Object> mapItem(InvoiceItemData item) {
        BigDecimal quantity = item.quantity();
        BigDecimal discountRate = item.discountRate() != null ? item.discountRate() : BigDecimal.ZERO;
        BigDecimal rate = item.taxRate() != null ? item.taxRate() : BigDecimal.ZERO;
        BigDecimal price;
        BigDecimal subtotal;
        BigDecimal discount;
        BigDecimal taxAmount;
        if (item.grossLineTotal() != null) {
            // Derive both components from the charged amount, not from a rounded unit base.
            BigDecimal taxFactor = BigDecimal.ONE.add(rate.divide(HUNDRED));
            BigDecimal grossBase = item.grossLineTotal().divide(taxFactor, 2, RoundingMode.HALF_UP);
            BigDecimal chargedAfterDiscount = money(item.grossLineTotal().multiply(HUNDRED.subtract(discountRate))
                    .divide(HUNDRED));
            subtotal = chargedAfterDiscount.divide(taxFactor, 2, RoundingMode.HALF_UP);
            discount = grossBase.subtract(subtotal);
            taxAmount = chargedAfterDiscount.subtract(subtotal);
            price = quantity.signum() == 0 ? item.unitPrice()
                    : grossBase.divide(quantity, 12, RoundingMode.HALF_UP);
        } else {
            // Manually supplied note/invoice prices remain tax-exclusive, including existing discounts.
            BigDecimal gross = money(quantity.multiply(item.unitPrice()));
            discount = money(gross.multiply(discountRate).divide(HUNDRED, 4, RoundingMode.HALF_UP));
            subtotal = gross.subtract(discount);
            taxAmount = money(subtotal.multiply(rate).divide(HUNDRED, 4, RoundingMode.HALF_UP));
            price = money(item.unitPrice());
        }

        Map<String, Object> tax = new LinkedHashMap<>();
        tax.put("taxCode", firstNonBlank(item.taxCode(), "01"));
        tax.put("taxAmount", taxAmount);
        tax.put("taxPercentage", rate.setScale(2, RoundingMode.HALF_UP).toPlainString());
        tax.put("taxableAmount", subtotal);

        Map<String, Object> line = new LinkedHashMap<>();
        line.put("description", item.productName());
        line.put("price", price);
        line.put("quantity", quantity);
        line.put("unitCode", firstNonBlank(item.unitMeasureCode(), "94"));
        line.put("subtotal", subtotal);
        line.put("taxAmount", taxAmount);
        if (discount.signum() > 0) {
            line.put("discountAmount", discount);
        }
        line.put("taxes", List.of(tax));
        line.put("standardCode", standardCode(item));
        return line;
    }

    /** Un código UNSPSC de 8 dígitos va con el estándar 001; si no, se identifica con el código propio del contribuyente (999). */
    private static Map<String, Object> standardCode(InvoiceItemData item) {
        Map<String, Object> standard = new LinkedHashMap<>();
        String code = item.standardCode() != null ? item.standardCode().trim() : "";
        if (code.matches("\\d{8}")) {
            standard.put("identificationId", code);
            standard.put("id", "001");
        } else {
            standard.put("identificationId", firstNonBlank(item.productCode(), "REF"));
            standard.put("id", "999");
        }
        return standard;
    }

    private static Map<String, Object> mapPayment(InvoiceRequest request) {
        String form = firstNonBlank(request.paymentForm(), "1");
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("paymentForm", form);
        payment.put("paymentMethod", firstNonBlank(request.paymentMethodCode(), "10"));
        if ("2".equals(form) && request.paymentDueDate() != null) {
            payment.put("paymentDueDate", request.paymentDueDate().toString());
        }
        return payment;
    }

    private static Map<String, Object> totals(BigDecimal gross, BigDecimal tax) {
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("grossTotal", gross);
        totals.put("taxableTotal", gross);
        totals.put("taxTotal", tax);
        totals.put("discountTotal", BigDecimal.ZERO.setScale(2));
        totals.put("chargeTotal", BigDecimal.ZERO.setScale(2));
        totals.put("advanceTotal", BigDecimal.ZERO.setScale(2));
        totals.put("payableTotal", gross.add(tax));
        totals.put("currencyCode", "COP");
        return totals;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
