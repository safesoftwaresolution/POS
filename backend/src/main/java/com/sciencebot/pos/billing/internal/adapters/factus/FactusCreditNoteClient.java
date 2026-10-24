package com.sciencebot.pos.billing.internal.adapters.factus;

import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.exception.FactusApiException;
import com.sciencebot.pos.billing.internal.adapters.CreditNoteProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.*;

/**
 * Cliente especializado para la emisión y gestión de Notas Crédito ante Factus API v2 y la DIAN.
 */
@Component
public class FactusCreditNoteClient implements CreditNoteProvider {

    private static final Logger log = LoggerFactory.getLogger(FactusCreditNoteClient.class);

    private final FactusClientHelper httpHelper;

    public FactusCreditNoteClient(FactusClientHelper httpHelper) {
        this.httpHelper = httpHelper;
    }

    @Override
    public String getProviderName() {
        return "factus";
    }

    @Override
    public CreditNoteResult emitCreditNote(CreateCreditNoteRequest request, Long storeId) {
        Objects.requireNonNull(request, "La solicitud de nota crédito no puede ser nula");
        if (request.referenceCode() == null || request.referenceCode().isBlank()) {
            return CreditNoteResult.error("El reference_code es obligatorio para emitir la nota crédito");
        }

        try {
            FactusAccess access = httpHelper.resolveAccess(storeId);
            Map<String, Object> payload = toFactusCreditNotePayload(request);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = httpHelper.authorizedPost("/v2/credit-notes/validate", access)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            if (isCreditNoteResponseValid(response)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");

                @SuppressWarnings("unchecked")
                Map<String, Object> targetMap = (data.get("credit_note") instanceof Map<?, ?> cnm)
                        ? (Map<String, Object>) cnm
                        : ((data.get("bill") instanceof Map<?, ?> bm) ? (Map<String, Object>) bm : data);

                String number = (String) targetMap.get("number");
                String cude = (String) targetMap.get("cude");
                if (cude == null) {
                    cude = (String) targetMap.get("cufe");
                }

                String qr = null;
                String publicUrl = null;
                if (data.get("links") instanceof Map<?, ?> linksMap) {
                    qr = (String) linksMap.get("qr");
                    publicUrl = (String) linksMap.get("public_url");
                }
                if (qr == null) qr = (String) targetMap.get("qr");
                if (publicUrl == null) publicUrl = (String) targetMap.get("public_url");

                return CreditNoteResult.success(number, cude, qr, publicUrl);
            } else {
                return CreditNoteResult.error("Respuesta de Factus incompleta o sin datos de nota crédito");
            }

        } catch (BillingNotEnabledException ex) {
            return CreditNoteResult.error(ex.getMessage());
        } catch (FactusApiException ex) {
            return CreditNoteResult.pending(httpHelper.truncateError(ex.getMessage()));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED || ex.getStatusCode() == HttpStatus.FORBIDDEN) {
                httpHelper.invalidateToken(storeId);
                return CreditNoteResult.pending("Fallo de autenticación con Factus API (HTTP " + ex.getStatusCode().value() + ")");
            }
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                return CreditNoteResult.error(httpHelper.truncateError("Conflicto DIAN/Factus (HTTP 409): " + ex.getResponseBodyAsString()));
            }
            return CreditNoteResult.rejected(httpHelper.truncateError("Error Validación DIAN (HTTP " + ex.getStatusCode().value() + "): " + ex.getResponseBodyAsString()));
        } catch (HttpServerErrorException | ResourceAccessException ex) {
            return CreditNoteResult.pending(httpHelper.truncateError("Factus no disponible / Timeout: " + ex.getMessage()));
        } catch (Exception ex) {
            log.error("Error imprevisto al emitir nota crédito en Factus con referencia: {}", request.referenceCode(), ex);
            return CreditNoteResult.error(httpHelper.truncateError("Error interno: " + ex.getMessage()));
        }
    }

    @Override
    public boolean deleteUnvalidatedCreditNote(String referenceCode, Long storeId) {
        Objects.requireNonNull(referenceCode, "El reference_code es obligatorio para eliminar");
        try {
            httpHelper.authorizedDelete("/v2/credit-notes/reference/" + referenceCode, storeId)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Nota crédito no validada eliminada exitosamente en Factus: {}", referenceCode);
            return true;
        } catch (Exception ex) {
            log.error("Error al eliminar nota crédito no validada con referencia {}: {}", referenceCode, ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public byte[] downloadCreditNotePDF(String creditNoteNumber, Long storeId) {
        Objects.requireNonNull(creditNoteNumber, "El número de nota crédito es obligatorio");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = httpHelper.authorizedGet("/v2/credit-notes/" + creditNoteNumber + "/download-pdf", storeId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.get("data") instanceof Map<?, ?> dataMap) {
                String b64 = (String) dataMap.get("pdf_base_64_encoded");
                if (b64 != null && !b64.isBlank()) {
                    return Base64.getDecoder().decode(b64);
                }
            }
        } catch (Exception ex) {
            log.error("Error al descargar PDF de nota crédito {}: {}", creditNoteNumber, ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public byte[] downloadCreditNoteXML(String creditNoteNumber, Long storeId) {
        Objects.requireNonNull(creditNoteNumber, "El número de nota crédito es obligatorio");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = httpHelper.authorizedGet("/v2/credit-notes/" + creditNoteNumber + "/download-xml", storeId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.get("data") instanceof Map<?, ?> dataMap) {
                String b64 = (String) dataMap.get("xml_base_64_encoded");
                if (b64 != null && !b64.isBlank()) {
                    return Base64.getDecoder().decode(b64);
                }
            }
        } catch (Exception ex) {
            log.error("Error al descargar XML de nota crédito {}: {}", creditNoteNumber, ex.getMessage(), ex);
        }
        return null;
    }

    public Map<String, Object> toFactusCreditNotePayload(CreateCreditNoteRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("reference_code", request.referenceCode());
        payload.put("correction_concept_code", request.correctionConceptCode());
        payload.put("customization_id", request.customizationId() != null ? request.customizationId() : "20");

        if (request.billNumber() != null && !request.billNumber().isBlank()) {
            payload.put("bill_number", request.billNumber().trim());
        }

        if (request.numberingRangeId() != null && request.numberingRangeId() > 0) {
            payload.put("numbering_range_id", request.numberingRangeId());
        }

        if (request.observation() != null && !request.observation().isBlank()) {
            payload.put("observation", request.observation().length() > 500
                    ? request.observation().substring(0, 500)
                    : request.observation());
        }

        // payment_details
        if (request.paymentDetails() != null && !request.paymentDetails().isEmpty()) {
            List<Map<String, Object>> paymentList = new ArrayList<>();
            for (var p : request.paymentDetails()) {
                Map<String, Object> paymentMap = new LinkedHashMap<>();
                paymentMap.put("payment_form", p.paymentForm() != null ? p.paymentForm() : "1");
                paymentMap.put("payment_method_code", p.paymentMethodCode() != null ? p.paymentMethodCode() : "10");
                if (p.referenceCode() != null && !p.referenceCode().isBlank()) {
                    paymentMap.put("reference_code", p.referenceCode());
                }
                paymentMap.put("amount", p.amount() != null ? p.amount() : "0.00");
                paymentList.add(paymentMap);
            }
            payload.put("payment_details", paymentList);
        }

        // customer
        if (request.customer() != null) {
            var c = request.customer();
            Map<String, Object> customerMap = new LinkedHashMap<>();
            customerMap.put("identification_document_code", c.identificationDocumentCode() != null ? c.identificationDocumentCode() : "13");
            String rawId = c.identification() != null ? c.identification().trim() : "222222222222";
            customerMap.put("identification", rawId.replaceAll("[^0-9a-zA-Z]", ""));

            if (c.dv() != null && !c.dv().isBlank()) {
                customerMap.put("dv", c.dv().trim());
            }

            String orgCode = c.legalOrganizationCode() != null ? c.legalOrganizationCode() : "2";
            customerMap.put("legal_organization_code", orgCode);
            customerMap.put("tribute_code", c.tributeCode() != null ? c.tributeCode() : "ZZ");
            customerMap.put("responsibilities", c.responsibilities() != null && !c.responsibilities().isEmpty()
                    ? c.responsibilities()
                    : List.of("R-99-PN"));

            if ("1".equals(orgCode)) {
                String company = c.company() != null ? c.company() : (c.names() != null ? c.names() : "Empresa");
                customerMap.put("company", company);
                if (c.tradeName() != null && !c.tradeName().isBlank()) {
                    customerMap.put("trade_name", c.tradeName());
                }
            } else {
                String names = c.names() != null ? c.names() : (c.company() != null ? c.company() : "Consumidor Final");
                customerMap.put("names", names);
            }

            if (c.address() != null && !c.address().isBlank()) customerMap.put("address", c.address());
            if (c.email() != null && !c.email().isBlank()) customerMap.put("email", c.email());
            if (c.phone() != null && !c.phone().isBlank()) customerMap.put("phone", c.phone());
            customerMap.put("country_code", c.countryCode() != null ? c.countryCode() : "CO");
            if (c.municipalityCode() != null && !c.municipalityCode().isBlank()) {
                customerMap.put("municipality_code", c.municipalityCode());
            }

            payload.put("customer", customerMap);
        }

        // items
        if (request.items() != null && !request.items().isEmpty()) {
            List<Map<String, Object>> itemsList = new ArrayList<>();
            for (var item : request.items()) {
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("code_reference", item.codeReference() != null ? item.codeReference() : "REF-ITEM");
                itemMap.put("name", item.name() != null ? item.name() : "Producto");
                itemMap.put("quantity", item.quantity() != null ? item.quantity() : "1.00");
                itemMap.put("discount_rate", item.discountRate() != null ? item.discountRate() : "0.00");
                itemMap.put("price", item.price() != null ? item.price() : "0.00");
                itemMap.put("unit_measure_code", item.unitMeasureCode() != null ? item.unitMeasureCode() : "94");
                itemMap.put("standard_code", item.standardCode() != null ? item.standardCode() : "999");

                List<Map<String, Object>> taxesList = new ArrayList<>();
                if (item.taxes() != null) {
                    for (var tax : item.taxes()) {
                        Map<String, Object> taxMap = new LinkedHashMap<>();
                        taxMap.put("code", tax.code() != null ? tax.code() : "01");
                        taxMap.put("rate", tax.rate() != null ? tax.rate() : "0.00");
                        taxesList.add(taxMap);
                    }
                }
                itemMap.put("taxes", taxesList);
                itemsList.add(itemMap);
            }
            payload.put("items", itemsList);
        }

        return payload;
    }

    private boolean isCreditNoteResponseValid(Map<String, Object> response) {
        if (response == null || !response.containsKey("data") || response.get("data") == null) {
            return false;
        }
        if (!(response.get("data") instanceof Map<?, ?> data)) {
            return false;
        }
        return data.containsKey("number") || data.containsKey("cude") || data.containsKey("cufe")
                || (data.containsKey("credit_note") && data.get("credit_note") instanceof Map<?, ?>)
                || (data.containsKey("bill") && data.get("bill") instanceof Map<?, ?>);
    }
}
