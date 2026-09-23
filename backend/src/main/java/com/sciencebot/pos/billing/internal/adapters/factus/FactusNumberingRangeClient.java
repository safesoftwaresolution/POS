package com.sciencebot.pos.billing.internal.adapters.factus;

import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.internal.adapters.NumberingRangeProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Cliente especializado para la gestión y sincronización de rangos de numeración ante Factus y la DIAN.
 * Trabaja con las credenciales del local de la solicitud en curso. Si Factus rechaza la llamada o no
 * responde, lanza {@link com.sciencebot.pos.billing.exception.FactusApiException} con el mensaje del proveedor.
 */
@Component
public class FactusNumberingRangeClient implements NumberingRangeProvider {

    private static final String BASE_PATH = "/v2/numbering-ranges";

    private final FactusClientHelper httpHelper;
    private final FactusNumberingRangeMapper rangeMapper;

    public FactusNumberingRangeClient(FactusClientHelper httpHelper, FactusNumberingRangeMapper rangeMapper) {
        this.httpHelper = httpHelper;
        this.rangeMapper = rangeMapper;
    }

    @Override
    public String getProviderName() {
        return "factus";
    }

    @Override
    public List<NumberingRangeDto> queryDianNumberingRanges() {
        try {
            return rangeMapper.extractRangesList(getJson(BASE_PATH + "/dian"));
        } catch (Exception ex) {
            throw httpHelper.translate("No se pudieron consultar los rangos asociados en la DIAN", ex);
        }
    }

    @Override
    public List<NumberingRangeDto> listNumberingRanges() {
        try {
            return rangeMapper.extractRangesList(getJson(BASE_PATH));
        } catch (Exception ex) {
            throw httpHelper.translate("No se pudieron listar los rangos de numeración", ex);
        }
    }

    @Override
    public NumberingRangeDto getNumberingRange(Long numberingRangeId) {
        Objects.requireNonNull(numberingRangeId, "El ID del rango de numeración es obligatorio");
        try {
            return rangeMapper.extractSingle(getJson(BASE_PATH + "/" + numberingRangeId));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw httpHelper.translate("No se pudo consultar el rango de numeración " + numberingRangeId, ex);
        } catch (Exception ex) {
            throw httpHelper.translate("No se pudo consultar el rango de numeración " + numberingRangeId, ex);
        }
    }

    @Override
    public NumberingRangeDto createNumberingRange(CreateNumberingRangeRequest request) {
        Objects.requireNonNull(request, "La solicitud de rango de numeración no puede ser nula");
        try {
            Map<String, Object> response = sendJson(httpHelper.authorizedPost(BASE_PATH), rangeMapper.toCreatePayload(request));
            return rangeMapper.extractSingle(response);
        } catch (Exception ex) {
            throw httpHelper.translate("Factus no pudo registrar el rango de numeración", ex);
        }
    }

    @Override
    public void deleteNumberingRange(Long numberingRangeId) {
        Objects.requireNonNull(numberingRangeId, "El ID del rango de numeración es obligatorio");
        try {
            httpHelper.authorizedDelete(BASE_PATH + "/" + numberingRangeId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw httpHelper.translate("Factus no pudo eliminar el rango de numeración " + numberingRangeId, ex);
        }
    }

    @Override
    public NumberingRangeDto toggleNumberingRangeStatus(Long numberingRangeId) {
        Objects.requireNonNull(numberingRangeId, "El ID del rango de numeración es obligatorio");
        try {
            Map<String, Object> response = sendJson(
                    httpHelper.authorizedPatch(BASE_PATH + "/" + numberingRangeId + "/toggle-status"), null);
            return rangeMapper.extractSingle(response);
        } catch (Exception ex) {
            throw httpHelper.translate("Factus no pudo cambiar el estado del rango de numeración " + numberingRangeId, ex);
        }
    }

    @Override
    public NumberingRangeDto updateNumberingRangeCurrent(Long numberingRangeId, Long current) {
        Objects.requireNonNull(numberingRangeId, "El ID del rango de numeración es obligatorio");
        Objects.requireNonNull(current, "El consecutivo actual es obligatorio");
        try {
            Map<String, Object> response = sendJson(
                    httpHelper.authorizedPatch(BASE_PATH + "/" + numberingRangeId + "/current"),
                    Map.of("current", current));
            return rangeMapper.extractSingle(response);
        } catch (Exception ex) {
            throw httpHelper.translate("Factus no pudo actualizar el consecutivo del rango " + numberingRangeId, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getJson(String path) {
        return httpHelper.authorizedGet(path)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sendJson(org.springframework.web.client.RestClient.RequestBodySpec spec, Object body) {
        org.springframework.web.client.RestClient.RequestBodySpec request = spec
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            request = request.body(body);
        }
        return request.retrieve().body(Map.class);
    }
}
