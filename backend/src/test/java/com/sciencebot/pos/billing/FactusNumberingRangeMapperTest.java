package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusNumberingRangeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FactusNumberingRangeMapperTest {

    private FactusNumberingRangeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FactusNumberingRangeMapper();
    }

    @Test
    void toCreatePayload_SendsOnlyWhatFactusAccepts() {
        CreateNumberingRangeRequest request = new CreateNumberingRangeRequest("01", "SETP", "18764000001234", 7L);

        Map<String, Object> payload = mapper.toCreatePayload(request);

        assertEquals(Map.of(
                "document", "01",
                "prefix", "SETP",
                "resolution_number", "18764000001234",
                "current", "7"
        ), payload, "from/to/fechas/clave técnica los completa Factus desde la DIAN");
    }

    @Test
    void extractSingle_ReadsDataObjectOrRootObject() {
        Map<String, Object> range = Map.of("id", 9, "prefix", "SETP", "is_active", 0, "is_expired", 1, "document_name", "Factura");

        NumberingRangeDto fromData = mapper.extractSingle(Map.of("status", "OK", "data", range));
        NumberingRangeDto fromRoot = mapper.extractSingle(range);

        assertEquals(9L, fromData.id());
        assertFalse(fromData.isActive());
        assertTrue(fromData.isExpired());
        assertEquals("Factura", fromData.documentName());
        assertEquals(fromData, fromRoot);
        assertNull(mapper.extractSingle(Map.of("message", "sin rango")));
        assertNull(mapper.extractSingle(null));
    }

    @Test
    void extractRangesList_SingleObjectInData_ReturnsOneRange() {
        Map<String, Object> dianRange = Map.of("prefix", "SETP", "from", 1, "to", 5000, "resolution_number", "1876");

        List<NumberingRangeDto> list = mapper.extractRangesList(Map.of("data", dianRange));

        assertEquals(1, list.size());
        assertEquals("SETP", list.get(0).prefix());
        assertEquals(5000L, list.get(0).to());
    }

    @Test
    void toCreatePayload_NullRequest_ReturnsEmptyMap() {
        Map<String, Object> payload = mapper.toCreatePayload(null);
        assertNotNull(payload);
        assertTrue(payload.isEmpty());
    }

    @Test
    void mapToDto_ValidMap_MapsAllFields() {
        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("id", 10);
        rawMap.put("document", "01");
        rawMap.put("prefix", "SETP");
        rawMap.put("resolution_number", "RES-999");
        rawMap.put("from", 1);
        rawMap.put("to", 1000);
        rawMap.put("current", 50);
        rawMap.put("start_date", "2026-01-01");
        rawMap.put("end_date", "2026-12-31");
        rawMap.put("technical_key", "tech-key");
        rawMap.put("is_active", 1);

        NumberingRangeDto dto = mapper.mapToDto(rawMap);

        assertNotNull(dto);
        assertEquals(10L, dto.id());
        assertEquals("01", dto.document());
        assertEquals("SETP", dto.prefix());
        assertEquals("RES-999", dto.resolutionNumber());
        assertEquals(1L, dto.from());
        assertEquals(1000L, dto.to());
        assertEquals(50L, dto.current());
        assertEquals("2026-01-01", dto.startDate());
        assertEquals("2026-12-31", dto.endDate());
        assertEquals("tech-key", dto.technicalKey());
        assertTrue(dto.isActive());
    }

    @Test
    void mapToDto_NullMap_ReturnsNull() {
        assertNull(mapper.mapToDto(null));
    }

    @Test
    void extractRangesList_DirectList_ReturnsDtoList() {
        Map<String, Object> item1 = Map.of("id", 1, "prefix", "A");
        Map<String, Object> item2 = Map.of("id", 2, "prefix", "B");
        Map<String, Object> response = Map.of("data", List.of(item1, item2));

        List<NumberingRangeDto> list = mapper.extractRangesList(response);

        assertEquals(2, list.size());
        assertEquals(1L, list.get(0).id());
        assertEquals("A", list.get(0).prefix());
        assertEquals(2L, list.get(1).id());
        assertEquals("B", list.get(1).prefix());
    }

    @Test
    void extractRangesList_NestedDataList_ReturnsDtoList() {
        Map<String, Object> item = Map.of("id", 5, "prefix", "NESTED");
        Map<String, Object> innerData = Map.of("data", List.of(item));
        Map<String, Object> response = Map.of("data", innerData);

        List<NumberingRangeDto> list = mapper.extractRangesList(response);

        assertEquals(1, list.size());
        assertEquals(5L, list.get(0).id());
        assertEquals("NESTED", list.get(0).prefix());
    }

    @Test
    void extractRangesList_NullOrEmpty_ReturnsEmptyList() {
        assertTrue(mapper.extractRangesList(null).isEmpty());
        assertTrue(mapper.extractRangesList(Map.of()).isEmpty());
        assertTrue(mapper.extractRangesList(Map.of("data", "invalid")).isEmpty());
    }
}
