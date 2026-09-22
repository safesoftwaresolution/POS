package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.NumberingRangeDto;
import com.sciencebot.pos.billing.internal.adapters.NumberingRangeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementación del servicio de administración de Rangos de Numeración.
 */
@Service
public class NumberingRangeServiceImpl implements NumberingRangeService {

    private static final Logger log = LoggerFactory.getLogger(NumberingRangeServiceImpl.class);

    private final Map<String, NumberingRangeProvider> providersMap;

    @Value("${billing.provider:factus}")
    private String activeProviderName;

    public NumberingRangeServiceImpl(List<NumberingRangeProvider> providers) {
        this.providersMap = providers.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderName().toLowerCase(),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    @Override
    public List<NumberingRangeDto> queryDianNumberingRanges() {
        return resolveProvider().queryDianNumberingRanges();
    }

    @Override
    public List<NumberingRangeDto> listNumberingRanges() {
        return resolveProvider().listNumberingRanges();
    }

    @Override
    public NumberingRangeDto getNumberingRange(Long numberingRangeId) {
        return resolveProvider().getNumberingRange(numberingRangeId);
    }

    @Override
    public NumberingRangeDto createNumberingRange(CreateNumberingRangeRequest request) {
        return resolveProvider().createNumberingRange(request);
    }

    @Override
    public void deleteNumberingRange(Long numberingRangeId) {
        resolveProvider().deleteNumberingRange(numberingRangeId);
    }

    @Override
    public NumberingRangeDto toggleNumberingRangeStatus(Long numberingRangeId) {
        return resolveProvider().toggleNumberingRangeStatus(numberingRangeId);
    }

    @Override
    public NumberingRangeDto updateNumberingRangeCurrent(Long numberingRangeId, Long current) {
        return resolveProvider().updateNumberingRangeCurrent(numberingRangeId, current);
    }

    private NumberingRangeProvider resolveProvider() {
        String key = (activeProviderName != null) ? activeProviderName.trim().toLowerCase() : "factus";
        NumberingRangeProvider provider = providersMap.get(key);
        if (provider == null) {
            log.warn("Proveedor de facturación '{}' no encontrado para rangos de numeración. Utilizando 'mock'", key);
            provider = providersMap.get("mock");
        }
        if (provider == null) {
            throw new IllegalStateException("No hay ningún proveedor de rangos de numeración disponible.");
        }
        return provider;
    }
}
