package com.sciencebot.pos.services.internal.services;

import com.sciencebot.pos.categories.CategoryDeleteValidator;
import com.sciencebot.pos.categories.CategoryFacade;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.services.CreateServiceCommand;
import com.sciencebot.pos.services.ServiceDto;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.services.UpdateServiceCommand;
import com.sciencebot.pos.services.internal.entities.ServiceItem;
import com.sciencebot.pos.services.internal.mappers.ServiceMapper;
import com.sciencebot.pos.services.internal.repositories.ServiceItemRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ServiceCatalogServiceImpl implements ServiceFacade, CategoryDeleteValidator {

    private static final String DEFAULT_UNIT_MEASURE_CODE = "94";
    private static final BigDecimal MAX_TAX_RATE = BigDecimal.valueOf(100);

    private final ServiceItemRepository serviceRepository;
    private final ServiceMapper serviceMapper;
    private final CategoryFacade categoryFacade;

    public ServiceCatalogServiceImpl(
            ServiceItemRepository serviceRepository,
            ServiceMapper serviceMapper,
            @Lazy CategoryFacade categoryFacade) {
        this.serviceRepository = serviceRepository;
        this.serviceMapper = serviceMapper;
        this.categoryFacade = categoryFacade;
    }

    @Override
    public Optional<ServiceDto> getById(Long id) {
        return serviceRepository.findByIdAndStoreId(id, requireCurrentStoreId()).map(serviceMapper::toDto);
    }

    @Override
    @Transactional
    public ServiceDto createService(CreateServiceCommand command) {
        Long storeId = requireCurrentStoreId();
        if (command.code() == null || command.code().isBlank()) {
            throw new IllegalArgumentException("El código del servicio es obligatorio");
        }
        String code = command.code().trim();
        if (serviceRepository.existsByStoreIdAndCode(storeId, code)) {
            throw new IllegalArgumentException("Ya existe un servicio con el código: " + code);
        }
        requireName(command.name());
        requireCategory(command.categoryId());
        BigDecimal taxRate = command.taxRate() != null ? command.taxRate() : BigDecimal.ZERO;
        validatePriceAndTax(command.price(), taxRate);
        validateDuration(command.durationMinutes());

        ServiceItem service = new ServiceItem();
        service.setStoreId(storeId);
        service.setCode(code);
        service.setName(command.name().trim());
        service.setDescription(blankToNull(command.description()));
        service.setCategoryId(command.categoryId());
        service.setPrice(command.price());
        service.setTaxRate(taxRate);
        service.setUnspscCode(blankToNull(command.unspscCode()));
        service.setUnitMeasureCode(unitMeasureOrDefault(command.unitMeasureCode()));
        service.setDurationMinutes(command.durationMinutes());
        service.setActive(true);
        return serviceMapper.toDto(serviceRepository.save(service));
    }

    @Override
    @Transactional
    public ServiceDto updateService(Long id, UpdateServiceCommand command) {
        ServiceItem service = requireOwnService(id);
        if (command.name() != null) {
            requireName(command.name());
            service.setName(command.name().trim());
        }
        if (command.description() != null) {
            service.setDescription(blankToNull(command.description()));
        }
        if (command.categoryId() != null) {
            requireCategory(command.categoryId());
            service.setCategoryId(command.categoryId());
        }
        BigDecimal price = command.price() != null ? command.price() : service.getPrice();
        BigDecimal taxRate = command.taxRate() != null ? command.taxRate() : service.getTaxRate();
        validatePriceAndTax(price, taxRate);
        service.setPrice(price);
        service.setTaxRate(taxRate);
        if (command.unspscCode() != null) {
            service.setUnspscCode(blankToNull(command.unspscCode()));
        }
        if (command.unitMeasureCode() != null) {
            service.setUnitMeasureCode(unitMeasureOrDefault(command.unitMeasureCode()));
        }
        if (command.durationMinutes() != null) {
            validateDuration(command.durationMinutes());
            service.setDurationMinutes(command.durationMinutes());
        }
        return serviceMapper.toDto(serviceRepository.save(service));
    }

    @Override
    @Transactional
    public void changeStatus(Long id, boolean active) {
        ServiceItem service = requireOwnService(id);
        service.setActive(active);
        serviceRepository.save(service);
    }

    @Override
    @Transactional
    public void deleteService(Long id) {
        changeStatus(id, false);
    }

    @Override
    public Page<ServiceDto> searchServices(String search, Long categoryId, Boolean active, Pageable pageable) {
        String term = search == null || search.isBlank() ? null : search.trim().toLowerCase();
        return serviceRepository.searchInStore(requireCurrentStoreId(), term, categoryId, active, pageable)
                .map(serviceMapper::toDto);
    }

    @Override
    public void validateBeforeDelete(Long categoryId) {
        if (serviceRepository.existsByCategoryIdAndActiveTrue(categoryId)) {
            throw new IllegalStateException("No se puede eliminar la categoría porque tiene servicios asociados activos");
        }
    }

    private ServiceItem requireOwnService(Long id) {
        return serviceRepository.findByIdAndStoreId(id, requireCurrentStoreId())
                .orElseThrow(() -> new EntityNotFoundException("Servicio no encontrado con ID: " + id));
    }

    private void requireCategory(Long categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("La categoría es obligatoria");
        }
        categoryFacade.getById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("La categoría especificada no existe"));
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del servicio es obligatorio");
        }
        if (name.trim().length() > 100) {
            throw new IllegalArgumentException("El nombre del servicio no puede superar los 100 caracteres");
        }
    }

    private static void validatePriceAndTax(BigDecimal price, BigDecimal taxRate) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio del servicio debe ser mayor a cero");
        }
        if (taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(MAX_TAX_RATE) > 0) {
            throw new IllegalArgumentException("La tasa de IVA debe estar entre 0 y 100");
        }
    }

    private static void validateDuration(Integer durationMinutes) {
        if (durationMinutes != null && durationMinutes <= 0) {
            throw new IllegalArgumentException("La duración debe ser mayor a cero minutos");
        }
    }

    private static String unitMeasureOrDefault(String unitMeasureCode) {
        return unitMeasureCode == null || unitMeasureCode.isBlank() ? DEFAULT_UNIT_MEASURE_CODE : unitMeasureCode.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Long requireCurrentStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeId;
    }
}
