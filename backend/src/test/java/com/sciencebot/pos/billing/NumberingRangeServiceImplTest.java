package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.internal.adapters.NumberingRangeProvider;
import com.sciencebot.pos.billing.internal.adapters.mock.MockBillingAdapter;
import com.sciencebot.pos.billing.internal.services.NumberingRangeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class NumberingRangeServiceImplTest {

    @Mock
    private NumberingRangeProvider customProvider;

    private MockBillingAdapter mockAdapter;
    private NumberingRangeServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockAdapter = new MockBillingAdapter();

        when(customProvider.getProviderName()).thenReturn("factus");

        service = new NumberingRangeServiceImpl(List.of(mockAdapter, customProvider));
    }

    @Test
    void numberingRanges_MockProvider_ReturnsRanges() {
        ReflectionTestUtils.setField(service, "activeProviderName", "mock");

        List<NumberingRangeDto> dianRanges = service.queryDianNumberingRanges();
        assertNotNull(dianRanges);
        assertFalse(dianRanges.isEmpty());
        assertEquals("SETP", dianRanges.get(0).prefix());

        List<NumberingRangeDto> factusRanges = service.listNumberingRanges();
        assertNotNull(factusRanges);
        assertFalse(factusRanges.isEmpty());

        NumberingRangeDto singleRange = service.getNumberingRange(1L);
        assertNotNull(singleRange);
        assertEquals(1L, singleRange.id());

        CreateNumberingRangeRequest createReq = new CreateNumberingRangeRequest(
                "01", "SETP", "18764000001234", 1L
        );
        NumberingRangeDto created = service.createNumberingRange(createReq);
        assertNotNull(created);
        assertEquals(100L, created.id());

        service.deleteNumberingRange(1L);
        assertNotNull(service.toggleNumberingRangeStatus(1L));
        assertEquals(150L, service.updateNumberingRangeCurrent(1L, 150L).current());
    }
}
