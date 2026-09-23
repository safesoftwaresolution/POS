package com.sciencebot.pos.billing.internal.adapters.factus;

import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper responsable de la transformación bidireccional entre los DTOs de rangos
 * de numeración de la aplicación y la estructura JSON de la API Factus v2.
 */
@Component
public class FactusNumberingRangeMapper {

    /**
     * Construye el payload que Factus v2 espera para registrar un rango. Solo lleva lo que Factus
     * acepta: el resto (desde, hasta, fechas y clave técnica) lo obtiene Factus de la DIAN.
     * El consecutivo va como texto porque así lo define la documentación de "crear rango".
     */
    public Map<String, Object> toCreatePayload(CreateNumberingRangeRequest request) {
        if (request == null) {
            return Map.of();
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("document", request.document());
        payload.put("prefix", request.prefix());
        payload.put("resolution_number", request.resolutionNumber());
        payload.put("current", String.valueOf(request.current()));
        return payload;
    }

    /**
     * Extrae una lista de NumberingRangeDto desde una respuesta JSON de Factus API v2.
     * Acepta {@code data} como lista, como {@code data.data} (lista anidada) o como un único rango.
     */
    public List<NumberingRangeDto> extractRangesList(Map<String, Object> response) {
        List<NumberingRangeDto> list = new ArrayList<>();
        if (response == null || response.get("data") == null) {
            return list;
        }
        Object dataObj = response.get("data");
        if (dataObj instanceof List<?> dataList) {
            addAll(list, dataList);
        } else if (dataObj instanceof Map<?, ?> dataMap) {
            if (dataMap.get("data") instanceof List<?> nestedList) {
                addAll(list, nestedList);
            } else if (!dataMap.isEmpty()) {
                list.add(mapToDto(dataMap));
            }
        }
        return list;
    }

    /**
     * Extrae un único rango de una respuesta de Factus: normalmente en {@code data}, y si la
     * respuesta trae el rango directamente, de la raíz. Devuelve {@code null} si no hay rango.
     */
    public NumberingRangeDto extractSingle(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        if (response.get("data") instanceof Map<?, ?> dataMap) {
            return mapToDto(dataMap);
        }
        if (response.containsKey("id")) {
            return mapToDto(response);
        }
        return null;
    }

    /**
     * Transforma un mapa individual de respuesta de Factus a NumberingRangeDto.
     */
    public NumberingRangeDto mapToDto(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        return new NumberingRangeDto(
                parseLongValue(map.get("id")),
                text(map.get("document")),
                text(map.get("prefix")),
                text(map.get("resolution_number")),
                parseLongValue(map.get("from")),
                parseLongValue(map.get("to")),
                parseLongValue(map.get("current")),
                text(map.get("start_date")),
                text(map.get("end_date")),
                text(map.get("technical_key")),
                flag(map.get("is_active")),
                text(map.get("document_name")),
                flag(map.get("is_expired"))
        );
    }

    private void addAll(List<NumberingRangeDto> target, List<?> items) {
        for (Object item : items) {
            if (item instanceof Map<?, ?> itemMap) {
                target.add(mapToDto(itemMap));
            }
        }
    }

    private String text(Object value) {
        return value != null ? value.toString() : null;
    }

    /** Factus manda 1 o 0; se aceptan también booleanos. */
    private Boolean flag(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() == 1;
        }
        return null;
    }

    private Long parseLongValue(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
