package com.sciencebot.pos.billing.internal.adapters.factus;

import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.exception.FactusApiException;
import com.sciencebot.pos.billing.internal.adapters.InvoiceProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;
import java.util.Objects;

/**
 * Cliente especializado para la emisión y gestión de Facturas Electrónicas ante Factus API v2.
 */
@Component
public class FactusInvoiceClient implements InvoiceProvider {

    private static final Logger log = LoggerFactory.getLogger(FactusInvoiceClient.class);

    private final FactusClientHelper httpHelper;
    private final FactusSaleMapper saleMapper;

    public FactusInvoiceClient(FactusClientHelper httpHelper, FactusSaleMapper saleMapper) {
        this.httpHelper = httpHelper;
        this.saleMapper = saleMapper;
    }

    @Override
    public String getProviderName() {
        return "factus";
    }

    @Override
    public InvoiceResult emitInvoice(InvoiceRequest request) {
        Objects.requireNonNull(request, "La solicitud de factura no puede ser nula");
        if (request.saleId() == null) {
            return InvoiceResult.error("El ID de la venta es obligatorio para emitir la factura electrónica");
        }

        try {
            FactusAccess access = httpHelper.resolveAccess(request.storeId());
            int targetRangeId = (request.numberingRangeId() != null && request.numberingRangeId() > 0)
                    ? request.numberingRangeId()
                    : rangeOf(access);
            Map<String, Object> payload = saleMapper.toFactusRequestPayload(request, targetRangeId);

            Map<String, Object> response;
            try {
                response = postBill(access, payload);
            } catch (HttpClientErrorException ex) {
                if (ex.getStatusCode() != HttpStatus.CONFLICT) {
                    throw ex;
                }
                // 409: el reference_code (determinista por venta) ya existe en Factus sin validar, típico de un
                // intento anterior que expiró o fue rechazado. Se elimina ese borrador y se reenvía una vez.
                String reference = String.valueOf(payload.get("reference_code"));
                log.warn("Factus respondió 409 para la referencia {}; se elimina el borrador sin validar y se reintenta", reference);
                try {
                    httpHelper.authorizedDelete("/v1/bills/destroy/reference/" + reference, access)
                            .retrieve()
                            .toBodilessEntity();
                } catch (Exception deleteEx) {
                    log.error("No se pudo eliminar el borrador {} en Factus: {}", reference, deleteEx.getMessage());
                    throw ex;
                }
                response = postBill(access, payload);
            }

            if (isBillResponseValid(response)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");

                @SuppressWarnings("unchecked")
                Map<String, Object> billMap = (data.get("bill") instanceof Map<?, ?> bm)
                        ? (Map<String, Object>) bm
                        : data;

                String number = (String) billMap.get("number");
                String cufe = (String) billMap.get("cufe");

                String qr;
                String publicUrl;
                if (data.get("links") instanceof Map<?, ?> linksMap) {
                    qr = (String) linksMap.get("qr");
                    publicUrl = (String) linksMap.get("public_url");
                } else {
                    qr = (String) billMap.get("qr");
                    publicUrl = (String) billMap.get("public_url");
                }

                return InvoiceResult.success(number, cufe, qr, publicUrl);
            } else {
                return InvoiceResult.error("Respuesta de Factus incompleta o sin datos de factura");
            }

        } catch (BillingNotEnabledException ex) {
            return InvoiceResult.error(ex.getMessage());
        } catch (FactusApiException ex) {
            // No se pudo obtener el token (credenciales rechazadas o Factus caído): reintentable.
            return InvoiceResult.pending(httpHelper.truncateError(ex.getMessage()));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED || ex.getStatusCode() == HttpStatus.FORBIDDEN) {
                httpHelper.invalidateToken(request.storeId());
                return InvoiceResult.pending("Fallo de autenticación con Factus API (HTTP " + ex.getStatusCode().value() + ")");
            }
            return InvoiceResult.rejected(httpHelper.truncateError("Error Validación DIAN (HTTP " + ex.getStatusCode().value() + "): " + ex.getResponseBodyAsString()));
        } catch (HttpServerErrorException | ResourceAccessException ex) {
            return InvoiceResult.pending(httpHelper.truncateError("Factus no disponible / Timeout: " + ex.getMessage()));
        } catch (Exception ex) {
            log.error("Error imprevisto al emitir factura electrónica en Factus para venta ID: {}", request.saleId(), ex);
            return InvoiceResult.error(httpHelper.truncateError("Error interno: " + ex.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postBill(FactusAccess access, Map<String, Object> payload) {
        return httpHelper.authorizedPost("/v2/bills/validate", access)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);
    }

    @Override
    public InvoiceResult queryInvoice(String legalNumber) {
        return InvoiceResult.pending("Consulta de estado directo no implementada para número: " + legalNumber);
    }

    @Override
    public byte[] downloadInvoicePDF(String legalNumber) {
        try {
            return httpHelper.authorizedGet("/v1/bills/" + legalNumber + "/download-pdf")
                    .accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception ex) {
            log.error("Error al descargar PDF de factura legalNumber: {}", legalNumber, ex);
            return null;
        }
    }

    @Override
    public byte[] downloadInvoiceXML(String legalNumber) {
        try {
            return httpHelper.authorizedGet("/v1/bills/" + legalNumber + "/download-xml/")
                    .accept(MediaType.APPLICATION_XML, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_XML)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception ex) {
            log.error("Error al descargar XML de factura legalNumber: {}", legalNumber, ex);
            return null;
        }
    }

    @Override
    public boolean deleteUnvalidatedInvoice(String legalNumber) {
        try {
            httpHelper.authorizedDelete("/v1/bills/destroy/reference/" + legalNumber)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            log.error("Error al eliminar factura no validada legalNumber: {}", legalNumber, ex);
            return false;
        }
    }

    @Override
    public boolean sendInvoiceEmail(String legalNumber, String email) {
        Objects.requireNonNull(legalNumber, "El número de factura es obligatorio");
        Objects.requireNonNull(email, "El correo electrónico es obligatorio");

        try {
            httpHelper.authorizedPost("/v2/bills/" + legalNumber + "/send-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Factura {} enviada por correo a {}", legalNumber, email);
            return true;
        } catch (Exception ex) {
            log.error("Error al enviar factura {} por correo a {}: {}", legalNumber, email, ex.getMessage(), ex);
            return false;
        }
    }

    /** Rango de numeración del local para su entorno activo. Sin rango no se puede facturar. */
    private static int rangeOf(FactusAccess access) {
        Integer range = access.numberingRangeId();
        if (range == null || range <= 0) {
            throw new BillingNotEnabledException(
                    "El local no tiene un rango de numeración configurado para el entorno " + access.environment());
        }
        return range;
    }

    private boolean isBillResponseValid(Map<String, Object> response) {
        if (response == null || !response.containsKey("data") || response.get("data") == null) {
            return false;
        }
        if (!(response.get("data") instanceof Map<?, ?> data)) {
            return false;
        }
        return data.containsKey("number") || data.containsKey("cufe") ||
                (data.containsKey("bill") && data.get("bill") instanceof Map<?, ?>);
    }
}
