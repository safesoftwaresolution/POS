package com.sciencebot.pos.businesses;

import com.sciencebot.pos.businesses.internal.entities.BusinessAuditEntity;
import com.sciencebot.pos.businesses.internal.entities.BusinessEntity;
import com.sciencebot.pos.businesses.internal.mappers.BusinessMapper;
import com.sciencebot.pos.businesses.internal.repositories.BusinessAuditRepository;
import com.sciencebot.pos.businesses.internal.repositories.BusinessRepository;
import com.sciencebot.pos.businesses.internal.services.BusinessServiceImpl;
import com.sciencebot.pos.stores.CreateStoreCommand;
import com.sciencebot.pos.stores.StoreDto;
import com.sciencebot.pos.stores.StoreFacade;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BusinessServiceImplTest {

    @Mock private BusinessRepository businessRepository;
    @Mock private BusinessAuditRepository auditRepository;
    @Mock private StoreFacade storeFacade;
    @Mock private UserFacade userFacade;
    @Mock private org.springframework.beans.factory.ObjectProvider<com.sciencebot.pos.billing.api.BillingFacade> billingFacadeProvider;
    @Mock private com.sciencebot.pos.billing.api.BillingFacade billingFacade;

    private BusinessServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(billingFacadeProvider.getIfAvailable()).thenReturn(billingFacade);
        service = new BusinessServiceImpl(businessRepository, auditRepository, new BusinessMapper(), storeFacade, userFacade, billingFacadeProvider);
        when(businessRepository.save(any(BusinessEntity.class))).thenAnswer(inv -> {
            BusinessEntity b = inv.getArgument(0);
            if (b.getId() == null) b.setId(7L);
            return b;
        });
    }

    private static StoreDto store(long id, String status) {
        return new StoreDto(id, "Local " + id, null, null, null, "s" + id + "@x.co", null, null, null, status,
                true, null, null, "BASIC", "RETAIL", "DEFAULT", List.of("RETAIL"), List.of());
    }

    private static UserDto owner(String role, boolean active, Long storeId) {
        return new UserDto(5L, "Dueño", "dueno", "d@x.co", role, active, storeId, true);
    }

    private static CreateBusinessCommand command(CatalogMode catalog) {
        return new CreateBusinessCommand("  Mi Negocio ", 5L, 1L, BillingMode.SHARED_TAX_ID, catalog, 3);
    }

    private static BusinessEntity business(int maxBranches) {
        BusinessEntity b = new BusinessEntity();
        b.setId(7L);
        b.setName("Mi Negocio");
        b.setOwnerUserId(5L);
        b.setMainStoreId(1L);
        b.setBillingMode(BillingMode.SEPARATE_TAX_ID);
        b.setCatalogMode(CatalogMode.INDEPENDENT);
        b.setMaxBranches(maxBranches);
        return b;
    }

    @Test
    void create_storesTheModes_attachesTheMainStore_andAudits() {
        when(userFacade.getById(5L)).thenReturn(owner("ADMINISTRATOR", true, 1L));
        when(storeFacade.getById(1L)).thenReturn(store(1L, "ACTIVE"));
        when(storeFacade.listByBusiness(7L)).thenReturn(List.of(store(1L, "ACTIVE")));

        BusinessDto dto = service.create(command(null));

        assertEquals("Mi Negocio", dto.name());
        assertEquals(BillingMode.SHARED_TAX_ID, dto.billingMode());
        assertEquals(CatalogMode.INDEPENDENT, dto.catalogMode());
        assertEquals(3, dto.maxBranches());
        assertEquals(1, dto.branches().size());
        assertTrue(dto.branches().get(0).main());
        verify(storeFacade).attachToBusiness(1L, 7L);
        ArgumentCaptor<BusinessAuditEntity> audit = ArgumentCaptor.forClass(BusinessAuditEntity.class);
        verify(auditRepository).save(audit.capture());
        assertEquals("CREATED", audit.getValue().getAction());
        assertEquals(1L, audit.getValue().getAffectedStoreId());
    }

    @Test
    void create_rejectsTheSharedCatalog_untilPhaseTwo() {
        var ex = assertThrows(IllegalArgumentException.class, () -> service.create(command(CatalogMode.SHARED)));
        assertTrue(ex.getMessage().contains("catálogo compartido"));
        verify(businessRepository, never()).save(any());
        verify(storeFacade, never()).attachToBusiness(anyLong(), anyLong());
    }

    @Test
    void create_rejectsAnOwnerWhoIsNotAnActiveAdministrator() {
        when(userFacade.getById(5L)).thenReturn(owner("SELLER", true, 1L));
        assertThrows(IllegalArgumentException.class, () -> service.create(command(null)));

        when(userFacade.getById(5L)).thenReturn(owner("ADMINISTRATOR", false, 1L));
        assertThrows(IllegalArgumentException.class, () -> service.create(command(null)));
        verify(businessRepository, never()).save(any());
    }

    @Test
    void create_rejectsAnOwnerWhoDoesNotAdministerTheMainStore() {
        when(userFacade.getById(5L)).thenReturn(owner("ADMINISTRATOR", true, 2L));
        assertThrows(IllegalArgumentException.class, () -> service.create(command(null)));
        verify(businessRepository, never()).save(any());
    }

    @Test
    void create_rejectsAnOwnerWhoAlreadyHasABusiness() {
        when(userFacade.getById(5L)).thenReturn(owner("ADMINISTRATOR", true, 1L));
        when(businessRepository.existsByOwnerUserId(5L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.create(command(null)));
        verify(businessRepository, never()).save(any());
    }

    @Test
    void create_rejectsARejectedMainStore() {
        when(userFacade.getById(5L)).thenReturn(owner("ADMINISTRATOR", true, 1L));
        when(storeFacade.getById(1L)).thenReturn(store(1L, "REJECTED"));
        assertThrows(IllegalArgumentException.class, () -> service.create(command(null)));
        verify(businessRepository, never()).save(any());
    }

    @Test
    void addBranch_attachesTheStore_whenUnderTheLimit() {
        when(businessRepository.findById(7L)).thenReturn(Optional.of(business(2)));
        when(storeFacade.getById(2L)).thenReturn(store(2L, "ACTIVE"));
        when(storeFacade.listByBusiness(7L)).thenReturn(List.of(store(1L, "ACTIVE")));

        service.addBranch(7L, 2L);

        verify(storeFacade).attachToBusiness(2L, 7L);
        ArgumentCaptor<BusinessAuditEntity> audit = ArgumentCaptor.forClass(BusinessAuditEntity.class);
        verify(auditRepository).save(audit.capture());
        assertEquals("BRANCH_ADDED", audit.getValue().getAction());
        assertEquals(2L, audit.getValue().getAffectedStoreId());
    }

    @Test
    void addBranch_failsWhenTheBranchLimitIsReached() {
        when(businessRepository.findById(7L)).thenReturn(Optional.of(business(1)));
        when(storeFacade.getById(2L)).thenReturn(store(2L, "ACTIVE"));
        when(storeFacade.listByBusiness(7L)).thenReturn(List.of(store(1L, "ACTIVE")));

        assertThrows(IllegalStateException.class, () -> service.addBranch(7L, 2L));
        verify(storeFacade, never()).attachToBusiness(anyLong(), anyLong());
    }

    @Test
    void addBranch_rejectsARejectedStore() {
        when(businessRepository.findById(7L)).thenReturn(Optional.of(business(5)));
        when(storeFacade.getById(2L)).thenReturn(store(2L, "REJECTED"));
        assertThrows(IllegalArgumentException.class, () -> service.addBranch(7L, 2L));
        verify(storeFacade, never()).attachToBusiness(anyLong(), anyLong());
    }

    @Test
    void removeBranch_neverRemovesTheMainStore() {
        when(businessRepository.findById(7L)).thenReturn(Optional.of(business(5)));
        assertThrows(IllegalArgumentException.class, () -> service.removeBranch(7L, 1L));
        verify(storeFacade, never()).detachFromBusiness(anyLong(), anyLong());
    }

    @Test
    void removeBranch_detachesAndAudits() {
        when(businessRepository.findById(7L)).thenReturn(Optional.of(business(5)));
        when(storeFacade.listByBusiness(7L)).thenReturn(List.of(store(1L, "ACTIVE")));

        service.removeBranch(7L, 2L);

        verify(storeFacade).detachFromBusiness(2L, 7L);
        ArgumentCaptor<BusinessAuditEntity> audit = ArgumentCaptor.forClass(BusinessAuditEntity.class);
        verify(auditRepository).save(audit.capture());
        assertEquals("BRANCH_REMOVED", audit.getValue().getAction());
    }

    @Test
    void update_cannotLowerTheLimitBelowTheCurrentBranches() {
        when(businessRepository.findById(7L)).thenReturn(Optional.of(business(5)));
        when(storeFacade.listByBusiness(7L)).thenReturn(List.of(store(1L, "ACTIVE"), store(2L, "ACTIVE")));

        assertThrows(IllegalArgumentException.class,
                () -> service.update(7L, new UpdateBusinessCommand(null, 1)));
        verify(businessRepository, never()).save(any());
    }

    @Test
    void update_changesOnlyTheGivenFields() {
        BusinessEntity existing = business(5);
        when(businessRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(storeFacade.listByBusiness(7L)).thenReturn(List.of(store(1L, "ACTIVE")));

        BusinessDto dto = service.update(7L, new UpdateBusinessCommand(" Nuevo ", null));

        assertEquals("Nuevo", dto.name());
        assertEquals(5, dto.maxBranches());
        assertEquals(BillingMode.SEPARATE_TAX_ID, dto.billingMode());
    }

    @Test
    void accessibleBranchStatus_returnsTheStatusForABranchOfTheOwnersBusiness() {
        when(userFacade.findByUsername("dueno")).thenReturn(Optional.of(owner("ADMINISTRATOR", true, 1L)));
        when(businessRepository.findByOwnerUserId(5L)).thenReturn(Optional.of(business(3)));
        when(storeFacade.getBusinessId(2L)).thenReturn(7L);
        when(storeFacade.getStoreStatus(2L)).thenReturn("PENDING_VERIFICATION");

        assertEquals(Optional.of("PENDING_VERIFICATION"), service.accessibleBranchStatus("dueno", 2L));
    }

    @Test
    void accessibleBranchStatus_isEmptyForAStoreOfAnotherBusinessOrAStandaloneOne() {
        when(userFacade.findByUsername("dueno")).thenReturn(Optional.of(owner("ADMINISTRATOR", true, 1L)));
        when(businessRepository.findByOwnerUserId(5L)).thenReturn(Optional.of(business(3)));
        when(storeFacade.getBusinessId(3L)).thenReturn(8L);
        when(storeFacade.getBusinessId(4L)).thenReturn(null);

        assertTrue(service.accessibleBranchStatus("dueno", 3L).isEmpty());
        assertTrue(service.accessibleBranchStatus("dueno", 4L).isEmpty());
        assertTrue(service.accessibleBranchStatus("dueno", null).isEmpty());
    }

    @Test
    void accessibleBranchStatus_isEmptyWhenTheUserOwnsNoBusinessOrIsNotAnActiveAdministrator() {
        when(userFacade.findByUsername("dueno")).thenReturn(Optional.of(owner("ADMINISTRATOR", true, 1L)));
        when(businessRepository.findByOwnerUserId(5L)).thenReturn(Optional.empty());
        assertTrue(service.accessibleBranchStatus("dueno", 2L).isEmpty());

        when(userFacade.findByUsername("dueno")).thenReturn(Optional.of(owner("SELLER", true, 1L)));
        assertTrue(service.accessibleBranchStatus("dueno", 2L).isEmpty());

        when(userFacade.findByUsername("dueno")).thenReturn(Optional.of(owner("ADMINISTRATOR", false, 1L)));
        assertTrue(service.accessibleBranchStatus("dueno", 2L).isEmpty());
        verify(storeFacade, never()).getStoreStatus(anyLong());
    }

    @Test
    void findByOwnerUsername_returnsTheBusinessOfTheOwner() {
        when(userFacade.findByUsername("dueno")).thenReturn(Optional.of(owner("ADMINISTRATOR", true, 1L)));
        when(businessRepository.findByOwnerUserId(5L)).thenReturn(Optional.of(business(3)));
        when(storeFacade.listByBusiness(7L)).thenReturn(List.of(store(1L, "ACTIVE")));

        assertEquals(7L, service.findByOwnerUsername("dueno").orElseThrow().id());
    }

    private void ownerWithBusiness(BusinessEntity business) {
        when(userFacade.findByUsername("dueno")).thenReturn(Optional.of(owner("ADMINISTRATOR", true, 1L)));
        when(businessRepository.findByOwnerUserId(5L)).thenReturn(Optional.of(business));
        when(businessRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(business));
    }

    private static StoreDto mainStore(Long categoryId, String taxId) {
        return new StoreDto(1L, "Principal", categoryId, null, null, "p@x.co", null, null, taxId, "ACTIVE",
                true, null, null, "BASIC", "RETAIL", "DEFAULT", List.of("RETAIL"), List.of());
    }

    private static CreateStoreCommand branchCommand(Long categoryId, String taxId) {
        return new CreateStoreCommand("Sucursal Norte", categoryId, "300", "norte@x.co", null, "Calle 1", taxId);
    }

    @Test
    void createBranch_sharedTaxId_usesTheMainStoresTaxIdAndCategory() {
        BusinessEntity business = business(3);
        business.setBillingMode(BillingMode.SHARED_TAX_ID);
        ownerWithBusiness(business);
        when(storeFacade.listByBusiness(7L)).thenReturn(List.of(store(1L, "ACTIVE")));
        when(storeFacade.getById(1L)).thenReturn(mainStore(4L, "900123"));
        when(storeFacade.createBranch(eq(7L), any())).thenReturn(store(2L, "PENDING_VERIFICATION"));

        BranchDto branch = service.createBranch("dueno", branchCommand(null, "999999"));

        ArgumentCaptor<CreateStoreCommand> sent = ArgumentCaptor.forClass(CreateStoreCommand.class);
        verify(storeFacade).createBranch(eq(7L), sent.capture());
        assertEquals("900123", sent.getValue().taxId(), "the client-supplied tax id must not win in shared mode");
        assertEquals(4L, sent.getValue().storeCategoryId());
        assertEquals("PENDING_VERIFICATION", branch.status());
        assertFalse(branch.main());
        ArgumentCaptor<BusinessAuditEntity> audit = ArgumentCaptor.forClass(BusinessAuditEntity.class);
        verify(auditRepository).save(audit.capture());
        assertEquals("BRANCH_CREATED", audit.getValue().getAction());
    }

    @Test
    void createBranch_separateTaxId_keepsTheTaxIdAndCategoryTheOwnerSent() {
        ownerWithBusiness(business(3));
        when(storeFacade.listByBusiness(7L)).thenReturn(List.of(store(1L, "ACTIVE")));
        when(storeFacade.getById(1L)).thenReturn(mainStore(4L, "900123"));
        when(storeFacade.createBranch(eq(7L), any())).thenReturn(store(2L, "PENDING_VERIFICATION"));

        service.createBranch("dueno", branchCommand(9L, "800777"));

        ArgumentCaptor<CreateStoreCommand> sent = ArgumentCaptor.forClass(CreateStoreCommand.class);
        verify(storeFacade).createBranch(eq(7L), sent.capture());
        assertEquals("800777", sent.getValue().taxId());
        assertEquals(9L, sent.getValue().storeCategoryId());
    }

    @Test
    void createBranch_failsWhenTheBranchLimitIsReached() {
        ownerWithBusiness(business(1));
        when(storeFacade.listByBusiness(7L)).thenReturn(List.of(store(1L, "ACTIVE")));

        assertThrows(IllegalStateException.class, () -> service.createBranch("dueno", branchCommand(null, null)));
        verify(storeFacade, never()).createBranch(anyLong(), any());
    }

    @Test
    void createBranch_isDeniedToAUserWhoOwnsNoBusiness() {
        when(userFacade.findByUsername("dueno")).thenReturn(Optional.of(owner("ADMINISTRATOR", true, 1L)));
        when(businessRepository.findByOwnerUserId(5L)).thenReturn(Optional.empty());

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.createBranch("dueno", branchCommand(null, null)));
        verify(storeFacade, never()).createBranch(anyLong(), any());
    }

    @Test
    void dissolve_detachesEveryStoreThenDeletesTheBusiness() {
        BusinessEntity business = business(3);
        when(businessRepository.findById(7L)).thenReturn(Optional.of(business));
        when(storeFacade.listByBusiness(7L)).thenReturn(List.of(store(1L, "ACTIVE"), store(2L, "ACTIVE")));

        service.dissolve(7L);

        verify(storeFacade).detachFromBusiness(1L, 7L);
        verify(storeFacade).detachFromBusiness(2L, 7L);
        verify(businessRepository).delete(business);
        ArgumentCaptor<BusinessAuditEntity> audit = ArgumentCaptor.forClass(BusinessAuditEntity.class);
        verify(auditRepository).save(audit.capture());
        assertEquals("DISSOLVED", audit.getValue().getAction());
    }

    @Test
    void businessNameOwnedBy_returnsTheNameOnlyForAnOwner() {
        when(businessRepository.findByOwnerUserId(5L)).thenReturn(Optional.of(business(3)));
        when(businessRepository.findByOwnerUserId(6L)).thenReturn(Optional.empty());

        assertEquals(Optional.of("Mi Negocio"), service.businessNameOwnedBy(5L));
        assertTrue(service.businessNameOwnedBy(6L).isEmpty());
        assertTrue(service.businessNameOwnedBy(null).isEmpty());
    }

    @Test
    void billingSourceStoreId_isTheMainStoreOnlyForASharedTaxIdBusiness() {
        BusinessEntity shared = business(3);
        shared.setBillingMode(BillingMode.SHARED_TAX_ID);
        when(storeFacade.getBusinessId(2L)).thenReturn(7L);
        when(businessRepository.findById(7L)).thenReturn(Optional.of(shared));
        assertEquals(1L, service.billingSourceStoreId(2L));

        shared.setBillingMode(BillingMode.SEPARATE_TAX_ID);
        assertEquals(2L, service.billingSourceStoreId(2L));
    }

    @Test
    void billingSourceStoreId_isTheStoreItselfWhenItIsStandalone() {
        when(storeFacade.getBusinessId(4L)).thenReturn(null);
        assertEquals(4L, service.billingSourceStoreId(4L));
        assertNull(service.billingSourceStoreId(null));
        verify(businessRepository, never()).findById(any());
    }

    @Test
    void changeBillingMode_switchesTheModeAndAudits_whenNoStoreHasValidatedInvoices() {
        BusinessEntity business = business(3);
        when(businessRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(business));
        when(storeFacade.listByBusiness(7L)).thenReturn(List.of(store(1L, "ACTIVE"), store(2L, "ACTIVE")));
        when(billingFacade.hasValidatedInvoices(anyLong())).thenReturn(false);

        BusinessDto dto = service.changeBillingMode(7L, BillingMode.SHARED_TAX_ID);

        assertEquals(BillingMode.SHARED_TAX_ID, dto.billingMode());
        ArgumentCaptor<BusinessAuditEntity> audit = ArgumentCaptor.forClass(BusinessAuditEntity.class);
        verify(auditRepository).save(audit.capture());
        assertEquals("BILLING_MODE", audit.getValue().getAction());
        assertTrue(audit.getValue().getDetail().contains("SEPARATE_TAX_ID -> SHARED_TAX_ID"));
    }

    @Test
    void changeBillingMode_isRefusedOnceAnyStoreHasValidatedInvoices() {
        BusinessEntity business = business(3);
        when(businessRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(business));
        when(storeFacade.listByBusiness(7L)).thenReturn(List.of(store(1L, "ACTIVE"), store(2L, "ACTIVE")));
        when(billingFacade.hasValidatedInvoices(2L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.changeBillingMode(7L, BillingMode.SHARED_TAX_ID));
        assertEquals(BillingMode.SEPARATE_TAX_ID, business.getBillingMode());
        verify(auditRepository, never()).save(any());
    }

    @Test
    void changeBillingMode_failsClosedWhenBillingCannotBeChecked() {
        when(businessRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(business(3)));
        when(billingFacadeProvider.getIfAvailable()).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.changeBillingMode(7L, BillingMode.SHARED_TAX_ID));
    }

    @Test
    void changeBillingMode_toTheSameModeChangesNothing() {
        BusinessEntity business = business(3);
        when(businessRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(business));
        when(storeFacade.listByBusiness(7L)).thenReturn(List.of(store(1L, "ACTIVE")));

        service.changeBillingMode(7L, BillingMode.SEPARATE_TAX_ID);

        verifyNoInteractions(billingFacade);
        verify(auditRepository, never()).save(any());
    }

    @Test
    void getById_throwsNotFoundForAnUnknownBusiness() {
        when(businessRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.getById(99L));
    }
}
