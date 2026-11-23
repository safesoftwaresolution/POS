package com.sciencebot.pos.businesses.internal.services;

import com.sciencebot.pos.businesses.BillingMode;
import com.sciencebot.pos.businesses.BranchDto;
import com.sciencebot.pos.businesses.BusinessDto;
import com.sciencebot.pos.businesses.BusinessFacade;
import com.sciencebot.pos.businesses.CatalogMode;
import com.sciencebot.pos.businesses.CreateBusinessCommand;
import com.sciencebot.pos.businesses.UpdateBusinessCommand;
import com.sciencebot.pos.businesses.internal.entities.BusinessAuditEntity;
import com.sciencebot.pos.businesses.internal.entities.BusinessEntity;
import com.sciencebot.pos.businesses.internal.mappers.BusinessMapper;
import com.sciencebot.pos.businesses.internal.repositories.BusinessAuditRepository;
import com.sciencebot.pos.businesses.internal.repositories.BusinessRepository;
import com.sciencebot.pos.stores.CreateStoreCommand;
import com.sciencebot.pos.stores.StoreDto;
import com.sciencebot.pos.stores.StoreFacade;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BusinessServiceImpl implements BusinessFacade {

    private static final String ADMINISTRATOR = "ADMINISTRATOR";
    private static final String REJECTED = "REJECTED";

    private final BusinessRepository businessRepository;
    private final BusinessAuditRepository auditRepository;
    private final BusinessMapper mapper;
    private final StoreFacade storeFacade;
    private final UserFacade userFacade;
    /** Lazy: billing resolves the invoicing source through this module, so it cannot be injected directly. */
    private final org.springframework.beans.factory.ObjectProvider<com.sciencebot.pos.billing.api.BillingFacade> billingFacadeProvider;

    public BusinessServiceImpl(BusinessRepository businessRepository,
                               BusinessAuditRepository auditRepository,
                               BusinessMapper mapper,
                               StoreFacade storeFacade,
                               UserFacade userFacade,
                               org.springframework.beans.factory.ObjectProvider<com.sciencebot.pos.billing.api.BillingFacade> billingFacadeProvider) {
        this.billingFacadeProvider = billingFacadeProvider;
        this.businessRepository = businessRepository;
        this.auditRepository = auditRepository;
        this.mapper = mapper;
        this.storeFacade = storeFacade;
        this.userFacade = userFacade;
    }

    @Override
    @Transactional
    public BusinessDto create(CreateBusinessCommand command) {
        CatalogMode catalogMode = command.catalogMode() == null ? CatalogMode.INDEPENDENT : command.catalogMode();
        if (catalogMode != CatalogMode.INDEPENDENT) {
            throw new IllegalArgumentException("El catálogo compartido entre sucursales aún no está disponible.");
        }

        UserDto owner = userFacade.getById(command.ownerUserId());
        if (!ADMINISTRATOR.equals(owner.role()) || !owner.active()) {
            throw new IllegalArgumentException("El dueño debe ser un ADMINISTRATOR activo.");
        }
        if (!command.mainStoreId().equals(owner.storeId())) {
            throw new IllegalArgumentException("El dueño debe ser administrador de la sucursal principal.");
        }
        if (businessRepository.existsByOwnerUserId(owner.id())) {
            throw new IllegalStateException("El usuario ya es dueño de otro negocio.");
        }
        requireUsableStore(command.mainStoreId());

        BusinessEntity business = new BusinessEntity();
        business.setName(command.name().trim());
        business.setOwnerUserId(owner.id());
        business.setMainStoreId(command.mainStoreId());
        business.setBillingMode(command.billingMode());
        business.setCatalogMode(catalogMode);
        business.setMaxBranches(command.maxBranches());
        BusinessEntity saved = businessRepository.save(business);

        storeFacade.attachToBusiness(command.mainStoreId(), saved.getId());
        audit(saved, "CREATED", command.mainStoreId(),
                "Modo de facturación: " + saved.getBillingMode() + ", sucursales adicionales permitidas: " + saved.getMaxBranches());
        return toDto(saved);
    }

    @Override
    public BusinessDto getById(Long id) {
        return toDto(require(id));
    }

    @Override
    public java.util.Optional<BusinessDto> findByOwnerUsername(String username) {
        return ownedBy(username).map(this::toDto);
    }

    @Override
    public java.util.Optional<String> accessibleBranchStatus(String username, Long storeId) {
        if (storeId == null) {
            return java.util.Optional.empty();
        }
        return ownedBy(username)
                .filter(b -> b.getId().equals(storeFacade.getBusinessId(storeId)))
                .map(b -> storeFacade.getStoreStatus(storeId));
    }

    private java.util.Optional<BusinessEntity> ownedBy(String username) {
        if (username == null) {
            return java.util.Optional.empty();
        }
        return userFacade.findByUsername(username)
                .filter(u -> ADMINISTRATOR.equals(u.role()) && u.active())
                .flatMap(u -> businessRepository.findByOwnerUserId(u.id()));
    }

    @Override
    public Page<BusinessDto> search(String q, Pageable pageable) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        return businessRepository.search(query, pageable).map(this::toDto);
    }

    @Override
    @Transactional
    public BusinessDto update(Long id, UpdateBusinessCommand command) {
        BusinessEntity business = require(id);
        StringBuilder detail = new StringBuilder();
        if (command.name() != null) {
            business.setName(command.name().trim());
            detail.append("Nombre: ").append(business.getName()).append(". ");
        }
        if (command.maxBranches() != null) {
            int current = additionalBranches(id);
            if (command.maxBranches() < current) {
                throw new IllegalArgumentException("El máximo no puede ser menor a las sucursales adicionales que ya "
                        + "tiene el negocio (" + current + ").");
            }
            business.setMaxBranches(command.maxBranches());
            detail.append("Sucursales adicionales permitidas: ").append(command.maxBranches()).append(". ");
        }
        BusinessEntity saved = businessRepository.save(business);
        audit(saved, "UPDATED", null, detail.toString().trim());
        return toDto(saved);
    }

    @Override
    @Transactional
    public BusinessDto addBranch(Long businessId, Long storeId) {
        BusinessEntity business = require(businessId);
        requireUsableStore(storeId);
        if (additionalBranches(businessId) >= business.getMaxBranches()) {
            throw new IllegalStateException(
                    "El negocio ya alcanzó su máximo de sucursales adicionales (" + business.getMaxBranches() + ").");
        }
        storeFacade.attachToBusiness(storeId, businessId);
        audit(business, "BRANCH_ADDED", storeId, null);
        return toDto(business);
    }

    @Override
    @Transactional
    public BusinessDto removeBranch(Long businessId, Long storeId) {
        BusinessEntity business = require(businessId);
        if (business.getMainStoreId().equals(storeId)) {
            throw new IllegalArgumentException("La sucursal principal no se puede sacar del negocio.");
        }
        storeFacade.detachFromBusiness(storeId, businessId);
        audit(business, "BRANCH_REMOVED", storeId, null);
        return toDto(business);
    }

    @Override
    public Long billingSourceStoreId(Long storeId) {
        if (storeId == null) {
            return null;
        }
        Long businessId = storeFacade.getBusinessId(storeId);
        if (businessId == null) {
            return storeId;
        }
        return businessRepository.findById(businessId)
                .filter(b -> b.getBillingMode() == BillingMode.SHARED_TAX_ID)
                .map(BusinessEntity::getMainStoreId)
                .orElse(storeId);
    }

    @Override
    @Transactional
    public BusinessDto changeBillingMode(Long id, BillingMode billingMode) {
        BusinessEntity business = businessRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Negocio no encontrado con ID: " + id));
        if (business.getBillingMode() == billingMode) {
            return toDto(business);
        }
        com.sciencebot.pos.billing.api.BillingFacade billing = billingFacadeProvider == null ? null
                : billingFacadeProvider.getIfAvailable();
        if (billing == null) {
            throw new IllegalStateException("No se pudo verificar la facturación del negocio; intenta de nuevo.");
        }
        boolean hasValidated = storeFacade.listByBusiness(id).stream().anyMatch(s -> billing.hasValidatedInvoices(s.id()));
        if (hasValidated) {
            throw new IllegalStateException("No se puede cambiar el modo de facturación: alguna sucursal ya tiene "
                    + "facturas validadas por la DIAN, ligadas a la empresa con la que se emitieron.");
        }
        String detail = "Modo de facturación: " + business.getBillingMode() + " -> " + billingMode;
        business.setBillingMode(billingMode);
        BusinessEntity saved = businessRepository.save(business);
        audit(saved, "BILLING_MODE", null, detail);
        return toDto(saved);
    }

    @Override
    @Transactional
    public BranchDto createBranch(String ownerUsername, CreateStoreCommand command) {
        BusinessEntity owned = ownedBy(ownerUsername)
                .orElseThrow(() -> new AccessDeniedException("No eres dueño de un negocio."));
        BusinessEntity business = businessRepository.findByIdForUpdate(owned.getId())
                .orElseThrow(() -> new EntityNotFoundException("Negocio no encontrado con ID: " + owned.getId()));
        if (additionalBranches(business.getId()) >= business.getMaxBranches()) {
            throw new IllegalStateException(
                    "Tu negocio ya alcanzó su máximo de sucursales adicionales (" + business.getMaxBranches() + ").");
        }

        StoreDto main = storeFacade.getById(business.getMainStoreId());
        CreateStoreCommand effective = new CreateStoreCommand(
                command.name(),
                command.storeCategoryId() != null ? command.storeCategoryId() : main.storeCategoryId(),
                command.phone(),
                command.email(),
                command.website(),
                command.address(),
                // Mismo NIT: el de la sucursal principal manda, no lo que mande el cliente.
                business.getBillingMode() == BillingMode.SHARED_TAX_ID ? main.taxId() : command.taxId());
        StoreDto created = storeFacade.createBranch(business.getId(), effective);
        audit(business, "BRANCH_CREATED", created.id(), "Creada por el dueño; queda pendiente de revisión.");
        return new BranchDto(created.id(), created.name(), created.status(), false);
    }

    @Override
    @Transactional
    public void dissolve(Long id) {
        BusinessEntity business = require(id);
        storeFacade.listByBusiness(id).forEach(s -> storeFacade.detachFromBusiness(s.id(), id));
        audit(business, "DISSOLVED", null, "Negocio disuelto; los locales quedan independientes.");
        businessRepository.delete(business);
    }

    @Override
    public java.util.Optional<String> businessNameOwnedBy(Long userId) {
        return userId == null ? java.util.Optional.empty()
                : businessRepository.findByOwnerUserId(userId).map(BusinessEntity::getName);
    }

    /** Sucursales del negocio sin contar la principal, que es la que el tope {@code max_branches} no cuenta. */
    private int additionalBranches(Long businessId) {
        return Math.max(0, storeFacade.listByBusiness(businessId).size() - 1);
    }

    private void requireUsableStore(Long storeId) {
        StoreDto store = storeFacade.getById(storeId);
        if (REJECTED.equals(store.status())) {
            throw new IllegalArgumentException("Un local rechazado no puede ser parte de un negocio.");
        }
    }

    private BusinessEntity require(Long id) {
        return businessRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Negocio no encontrado con ID: " + id));
    }

    private BusinessDto toDto(BusinessEntity business) {
        List<StoreDto> stores = storeFacade.listByBusiness(business.getId());
        return mapper.toDto(business, stores);
    }

    private void audit(BusinessEntity business, String action, Long storeId, String detail) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        BusinessAuditEntity entry = new BusinessAuditEntity();
        entry.setBusinessId(business.getId());
        entry.setBusinessName(business.getName());
        entry.setAction(action);
        entry.setAffectedStoreId(storeId);
        entry.setDetail(detail);
        entry.setPerformedBy(authentication != null ? authentication.getName() : "system");
        auditRepository.save(entry);
    }
}
