package com.sciencebot.pos.stores;

import com.sciencebot.pos.stores.internal.entities.StoreDocumentEntity;
import com.sciencebot.pos.stores.internal.entities.StoreEntity;
import com.sciencebot.pos.stores.internal.mappers.StoreMapper;
import com.sciencebot.pos.stores.internal.repositories.StoreCategoryRepository;
import com.sciencebot.pos.stores.internal.repositories.StoreDocumentRepository;
import com.sciencebot.pos.stores.internal.repositories.StoreRepository;
import com.sciencebot.pos.stores.internal.services.StoreServiceImpl;
import com.sciencebot.pos.notifications.NotificationFacade;
import com.sciencebot.pos.storage.StorageFacade;
import com.sciencebot.pos.storage.StorageUploadResult;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class StoreServiceImplTest {

    @Mock private StoreRepository storeRepository;
    @Mock private StoreCategoryRepository categoryRepository;
    @Mock private StoreDocumentRepository documentRepository;
    @Mock private StoreMapper storeMapper;
    @Mock private UserFacade userFacade;
    @Mock private StorageFacade storageFacade;
    @Mock private NotificationFacade notificationFacade;
    @Mock private com.sciencebot.pos.stores.internal.repositories.StoreStatusAuditRepository statusAuditRepository;
    @Mock private com.sciencebot.pos.stores.internal.repositories.StoreStatusAuditQueryDao statusAuditQueryDao;
    @Mock private com.sciencebot.pos.stores.internal.repositories.StorePurgeDao storePurgeDao;
    @Mock private com.sciencebot.pos.stores.internal.repositories.StoreUserAuditRepository storeUserAuditRepository;
    @Mock private com.sciencebot.pos.stores.internal.repositories.PlanRepository planRepository;
    @Mock private com.sciencebot.pos.stores.internal.services.StoreApprovalMailer approvalMailer;
    @InjectMocks private StoreServiceImpl storeService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getMetrics_ReturnsAggregatedMetrics() {
        when(storeRepository.count()).thenReturn(10L);
        when(storeRepository.countByStatus("ACTIVE")).thenReturn(7L);
        when(storeRepository.countByStatus("INACTIVE")).thenReturn(1L);
        when(storeRepository.countByStatus("PENDING_VERIFICATION")).thenReturn(1L);
        when(storeRepository.countByStatus("SUSPENDED")).thenReturn(1L);
        when(storeRepository.countByEmailVerifiedTrue()).thenReturn(6L);

        StoreMetricsDto metrics = storeService.getMetrics();

        assertNotNull(metrics);
        assertEquals(10L, metrics.totalStores());
        assertEquals(7L, metrics.activeStores());
        assertEquals(1L, metrics.inactiveStores());
        assertEquals(1L, metrics.pendingVerification());
        assertEquals(1L, metrics.suspended());
        assertEquals(6L, metrics.emailVerified());
    }

    @Test
    void createStore_Success() {
        CreateStoreCommand command = new CreateStoreCommand("Tienda 1", 1L, "3001234567", "tienda1@test.com", "https://tienda1.com", "Calle 1", "900123");
        when(storeRepository.existsByEmail("tienda1@test.com")).thenReturn(false);

        StoreEntity saved = new StoreEntity();
        saved.setId(1L);
        saved.setName("Tienda 1");
        saved.setEmail("tienda1@test.com");
        when(storeRepository.save(any(StoreEntity.class))).thenReturn(saved);

        StoreDto dto = new StoreDto(1L, "Tienda 1", 1L, "Retail", "3001234567", "tienda1@test.com", "https://tienda1.com", "Calle 1", "900123", "PENDING_VERIFICATION", false, null, null, "BASIC");
        when(storeMapper.toDto(any(), any())).thenReturn(dto);

        StoreDto result = storeService.createStore(command);
        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("Tienda 1", result.name());
        verify(storeRepository, times(1)).save(any(StoreEntity.class));
    }

    @Test
    void changeStatus_ValidStatus_Success() {
        StoreEntity entity = new StoreEntity();
        entity.setId(1L);
        entity.setStatus("PENDING_VERIFICATION");

        when(storeRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(storeRepository.save(any())).thenReturn(entity);
        when(storeMapper.toDto(any(), any())).thenReturn(new StoreDto(1L, "Tienda", null, null, null, "t@test.com", null, null, null, "ACTIVE", false, null, null, "BASIC"));

        StoreDto result = storeService.changeStatus(1L, "ACTIVE");
        assertNotNull(result);
        assertEquals("ACTIVE", result.status());
    }

    private StoreEntity storeInStatus(String status) {
        StoreEntity entity = new StoreEntity();
        entity.setId(1L);
        entity.setName("Arqueo");
        entity.setEmail("local@test.com");
        entity.setStatus(status);
        when(storeRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(storeRepository.save(any())).thenReturn(entity);
        when(storeMapper.toDto(any(), any())).thenReturn(new StoreDto(1L, "Arqueo", null, null, null, "local@test.com", null, null, null, "ACTIVE", false, null, null, "BASIC"));
        return entity;
    }

    @Test
    void changeStatus_ApprovingAPendingStore_EmailsItsAdministrators() {
        storeInStatus("PENDING_VERIFICATION");
        when(userFacade.listActiveByStoreAndRoles(1L, List.of("ADMINISTRATOR"))).thenReturn(List.of(
                new UserDto(7L, "Dueno", "dueno", "dueno@test.com", "ADMINISTRATOR", true, 1L, true)));

        storeService.changeStatus(1L, "ACTIVE");

        verify(approvalMailer).sendApproved("Arqueo", List.of("dueno@test.com"));
    }

    @Test
    void changeStatus_ApprovingAStoreWithoutAdministrators_FallsBackToTheStoreEmail() {
        storeInStatus("PENDING_VERIFICATION");
        when(userFacade.listActiveByStoreAndRoles(1L, List.of("ADMINISTRATOR"))).thenReturn(List.of());

        storeService.changeStatus(1L, "ACTIVE");

        verify(approvalMailer).sendApproved("Arqueo", List.of("local@test.com"));
    }

    @Test
    void changeStatus_ReactivatingASuspendedStoreOrSuspendingOne_SendsNoApprovalEmail() {
        storeInStatus("SUSPENDED");
        storeService.changeStatus(1L, "ACTIVE");

        storeInStatus("ACTIVE");
        storeService.changeStatus(1L, "SUSPENDED");

        verifyNoInteractions(approvalMailer);
    }

    @Test
    void changeStatus_InvalidStatus_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> storeService.changeStatus(1L, "INVALID"));
        assertTrue(ex.getMessage().contains("Estado invalido"));
    }

    @Test
    void registerOwnStore_Success_AssignsStoreToOwner() {
        CreateStoreCommand command = new CreateStoreCommand("Tienda 1", 1L, "3001234567", "tienda1@test.com", "https://tienda1.com", "Calle 1", "900123");
        UserDto owner = new UserDto(5L, "Carlos", "carlos", "carlos@test.com", "ADMINISTRATOR", true, null, true);
        when(userFacade.findByUsername("carlos")).thenReturn(Optional.of(owner));
        when(storeRepository.existsByEmail("tienda1@test.com")).thenReturn(false);

        StoreEntity saved = new StoreEntity();
        saved.setId(9L);
        saved.setName("Tienda 1");
        when(storeRepository.save(any(StoreEntity.class))).thenReturn(saved);
        when(storeMapper.toDto(any(), any())).thenReturn(
                new StoreDto(9L, "Tienda 1", 1L, "Retail", "3001234567", "tienda1@test.com", "https://tienda1.com", "Calle 1", "900123", "PENDING_VERIFICATION", false, null, null, "BASIC"));

        StoreDto result = storeService.registerOwnStore("carlos", command);

        assertNotNull(result);
        assertEquals(9L, result.id());
        verify(userFacade, times(1)).assignStore(5L, 9L);
    }

    @Test
    void registerOwnStore_OwnerAlreadyHasStore_ThrowsException() {
        CreateStoreCommand command = new CreateStoreCommand("Tienda 1", 1L, "3001234567", "tienda1@test.com", "https://tienda1.com", "Calle 1", "900123");
        UserDto owner = new UserDto(5L, "Carlos", "carlos", "carlos@test.com", "ADMINISTRATOR", true, 2L, true);
        when(userFacade.findByUsername("carlos")).thenReturn(Optional.of(owner));

        assertThrows(IllegalArgumentException.class, () -> storeService.registerOwnStore("carlos", command));
        verify(storeRepository, never()).save(any());
    }

    @Test
    void registerOwnStore_NotAdministrator_ThrowsAccessDenied() {
        CreateStoreCommand command = new CreateStoreCommand("Tienda 1", 1L, "3001234567", "tienda1@test.com", "https://tienda1.com", "Calle 1", "900123");
        UserDto owner = new UserDto(5L, "Sofia", "sofia", "sofia@test.com", "SELLER", true, 1L, true);
        when(userFacade.findByUsername("sofia")).thenReturn(Optional.of(owner));

        assertThrows(AccessDeniedException.class, () -> storeService.registerOwnStore("sofia", command));
    }

    @Test
    void rejectStore_WithReason_Success() {
        StoreEntity entity = new StoreEntity();
        entity.setId(1L);
        entity.setStatus("PENDING_VERIFICATION");

        when(storeRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(storeRepository.save(any())).thenReturn(entity);
        when(storeMapper.toDto(any(), any())).thenReturn(
                new StoreDto(1L, "Tienda", null, null, null, "t@test.com", null, null, null, "REJECTED", false, "Documentos incompletos", null, "BASIC"));

        StoreDto result = storeService.rejectStore(1L, "Documentos incompletos");

        assertNotNull(result);
        assertEquals("REJECTED", result.status());
        assertEquals("Documentos incompletos", result.rejectionReason());
    }

    private StoreEntity storeWithStatus(String status) {
        StoreEntity store = new StoreEntity();
        store.setId(5L);
        store.setName("Tienda Rechazada");
        store.setEmail("rechazada@test.com");
        store.setTaxId("900555");
        store.setStatus(status);
        store.setRejectionReason("Documentos ilegibles");
        return store;
    }

    private UserDto candidateOwner(boolean verified, Long storeId, String role) {
        return new UserDto(21L, "Dueno Libre", "dueno.libre", "libre@test.com", role, true, storeId, verified);
    }

    @Test
    void assignOwner_Success_AssignsAndAuditsWithSnapshot() {
        when(storeRepository.findById(5L)).thenReturn(Optional.of(storeWithStatus("ACTIVE")));
        when(userFacade.getById(21L)).thenReturn(candidateOwner(true, null, "ADMINISTRATOR"));
        when(userFacade.listActiveByStoreAndRoles(5L, List.of("ADMINISTRATOR"))).thenReturn(List.of());

        storeService.assignOwner(5L, 21L);

        verify(userFacade).assignStore(21L, 5L);
        var captor = org.mockito.ArgumentCaptor.forClass(com.sciencebot.pos.stores.internal.entities.StoreUserAuditEntity.class);
        verify(storeUserAuditRepository).save(captor.capture());
        assertEquals("OWNER_ASSIGNED", captor.getValue().getAction());
        assertEquals(5L, captor.getValue().getStoreId());
        assertEquals("Tienda Rechazada", captor.getValue().getStoreName());
        assertEquals(21L, captor.getValue().getUserId());
        assertEquals("dueno.libre", captor.getValue().getUsername());
        assertEquals("ADMINISTRATOR", captor.getValue().getRole());
    }

    @Test
    void assignOwner_StoreAlreadyHasAnAdministrator_IsRefused() {
        when(storeRepository.findById(5L)).thenReturn(Optional.of(storeWithStatus("ACTIVE")));
        when(userFacade.getById(21L)).thenReturn(candidateOwner(true, null, "ADMINISTRATOR"));
        when(userFacade.listActiveByStoreAndRoles(5L, List.of("ADMINISTRATOR")))
                .thenReturn(List.of(candidateOwner(true, 5L, "ADMINISTRATOR")));

        assertThrows(IllegalArgumentException.class, () -> storeService.assignOwner(5L, 21L));

        verify(userFacade, never()).assignStore(anyLong(), anyLong());
        verifyNoInteractions(storeUserAuditRepository);
    }

    @Test
    void assignOwner_UserAlreadyHasStoreOrIsNotAdministratorOrUnverified_IsRefused() {
        when(storeRepository.findById(5L)).thenReturn(Optional.of(storeWithStatus("ACTIVE")));

        when(userFacade.getById(21L)).thenReturn(candidateOwner(true, 9L, "ADMINISTRATOR"));
        assertThrows(IllegalArgumentException.class, () -> storeService.assignOwner(5L, 21L));

        when(userFacade.getById(21L)).thenReturn(candidateOwner(true, null, "SELLER"));
        assertThrows(IllegalArgumentException.class, () -> storeService.assignOwner(5L, 21L));

        when(userFacade.getById(21L)).thenReturn(candidateOwner(false, null, "ADMINISTRATOR"));
        assertThrows(IllegalArgumentException.class, () -> storeService.assignOwner(5L, 21L));

        verify(userFacade, never()).assignStore(anyLong(), anyLong());
    }

    @Test
    void assignOwner_RejectedStoreOrUnknownStore_IsRefused() {
        when(storeRepository.findById(5L)).thenReturn(Optional.of(storeWithStatus("REJECTED")));
        assertThrows(IllegalArgumentException.class, () -> storeService.assignOwner(5L, 21L));

        when(storeRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> storeService.assignOwner(9L, 21L));
    }

    private com.sciencebot.pos.stores.internal.entities.PlanEntity plan(String code, String name, int extra, int order) {
        var p = new com.sciencebot.pos.stores.internal.entities.PlanEntity();
        p.setCode(code);
        p.setName(name);
        p.setMaxAdditionalUsers(extra);
        p.setSortOrder(order);
        return p;
    }

    @Test
    void listPlans_ReturnsPlansWithTotalActiveUserCap() {
        when(planRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(plan("BASIC", "Básico", 1, 1), plan("PRO", "Pro", 10, 3)));

        List<PlanDto> plans = storeService.listPlans();

        assertEquals(2, plans.size());
        assertEquals("BASIC", plans.get(0).code());
        assertEquals(1, plans.get(0).maxAdditionalUsers());
        assertEquals(2, plans.get(0).maxActiveUsers());
        assertEquals(11, plans.get(1).maxActiveUsers());
    }

    @Test
    void changePlan_Success_UpdatesPlanAndAudits() {
        StoreEntity store = storeWithStatus("ACTIVE");
        store.setPlanCode("BASIC");
        when(storeRepository.findById(5L)).thenReturn(Optional.of(store));
        when(planRepository.findById("PRO")).thenReturn(Optional.of(plan("PRO", "Pro", 10, 3)));
        when(planRepository.findById("BASIC")).thenReturn(Optional.of(plan("BASIC", "Básico", 1, 1)));
        when(storeRepository.save(any(StoreEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        storeService.changePlan(5L, " pro ");

        assertEquals("PRO", store.getPlanCode());
        var captor = org.mockito.ArgumentCaptor.forClass(com.sciencebot.pos.stores.internal.entities.StoreStatusAuditEntity.class);
        verify(statusAuditRepository).save(captor.capture());
        assertEquals("PLAN_CHANGED", captor.getValue().getNewStatus());
        assertEquals("Plan: Básico -> Pro", captor.getValue().getReason());
        assertEquals("Tienda Rechazada", captor.getValue().getStoreName());
    }

    @Test
    void changePlan_SamePlan_ChangesAndAuditsNothing() {
        StoreEntity store = storeWithStatus("ACTIVE");
        store.setPlanCode("PRO");
        when(storeRepository.findById(5L)).thenReturn(Optional.of(store));
        when(planRepository.findById("PRO")).thenReturn(Optional.of(plan("PRO", "Pro", 10, 3)));

        storeService.changePlan(5L, "PRO");

        verify(storeRepository, never()).save(any(StoreEntity.class));
        verifyNoInteractions(statusAuditRepository);
    }

    @Test
    void changePlan_InvalidPlanBlankOrUnknownStore_IsRefused() {
        when(planRepository.findById("NOPE")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> storeService.changePlan(5L, "NOPE"));
        assertThrows(IllegalArgumentException.class, () -> storeService.changePlan(5L, " "));
        assertThrows(IllegalArgumentException.class, () -> storeService.changePlan(5L, null));

        when(planRepository.findById("PRO")).thenReturn(Optional.of(plan("PRO", "Pro", 10, 3)));
        when(storeRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> storeService.changePlan(9L, "PRO"));
    }

    @Test
    void deleteStore_RejectedWithoutOperation_NeedsNoConfirmation_AuditsPurgesAndDeletesFiles() {
        when(storeRepository.findById(5L)).thenReturn(Optional.of(storeWithStatus("REJECTED")));
        when(storePurgeDao.findBusinessData(5L)).thenReturn(List.of());
        when(storePurgeDao.findDocumentUrls(5L)).thenReturn(List.of("https://cdn.test/documentos/abc.pdf", "https://x/otro.png"));

        storeService.deleteStore(5L, null);

        var captor = org.mockito.ArgumentCaptor.forClass(com.sciencebot.pos.stores.internal.entities.StoreStatusAuditEntity.class);
        verify(statusAuditRepository).save(captor.capture());
        assertEquals("DELETED", captor.getValue().getNewStatus());
        assertEquals("REJECTED", captor.getValue().getPreviousStatus());
        assertEquals("Tienda Rechazada", captor.getValue().getStoreName());
        assertEquals("rechazada@test.com", captor.getValue().getStoreEmail());
        assertEquals("900555", captor.getValue().getStoreTaxId());
        assertTrue(captor.getValue().getReason().contains("Documentos ilegibles"));
        verify(storePurgeDao).purge(5L);
        verify(storageFacade).deleteFile("documentos/abc.pdf");
        verify(storageFacade, times(1)).deleteFile(anyString());
    }

    @Test
    void deleteStore_ActiveStoreWithOperation_RequiresTheExactName() {
        when(storeRepository.findById(5L)).thenReturn(Optional.of(storeWithStatus("ACTIVE")));
        when(storePurgeDao.findBusinessData(5L)).thenReturn(List.of("sales", "products"));

        assertThrows(IllegalArgumentException.class, () -> storeService.deleteStore(5L, null));
        assertThrows(IllegalArgumentException.class, () -> storeService.deleteStore(5L, "otro nombre"));
        verify(storePurgeDao, never()).purge(anyLong());
        verifyNoInteractions(statusAuditRepository, storageFacade);

        storeService.deleteStore(5L, "  tienda rechazada ");

        var captor = org.mockito.ArgumentCaptor.forClass(com.sciencebot.pos.stores.internal.entities.StoreStatusAuditEntity.class);
        verify(statusAuditRepository).save(captor.capture());
        assertEquals("ACTIVE", captor.getValue().getPreviousStatus());
        assertTrue(captor.getValue().getReason().contains("sales, products"));
        verify(storePurgeDao).purge(5L);
    }

    @Test
    void deleteStore_WithValidatedElectronicInvoices_IsRefusedEvenWithConfirmation() {
        when(storeRepository.findById(5L)).thenReturn(Optional.of(storeWithStatus("ACTIVE")));
        when(storePurgeDao.countValidatedInvoices(5L)).thenReturn(3L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> storeService.deleteStore(5L, "Tienda Rechazada"));

        assertTrue(ex.getMessage().contains("DIAN"));
        verify(storePurgeDao, never()).purge(anyLong());
        verifyNoInteractions(statusAuditRepository, storageFacade);
    }

    @Test
    void deleteStore_UnknownStore_ThrowsNotFound() {
        when(storeRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> storeService.deleteStore(9L, null));
    }

    @Test
    void rejectStore_WithoutReason_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> storeService.rejectStore(1L, " "));
        assertTrue(ex.getMessage().contains("motivo"));
    }

    @Test
    void reviewDocument_Approved_Success() {
        StoreDocumentEntity doc = new StoreDocumentEntity();
        doc.setId(10L);
        doc.setStoreId(1L);
        doc.setStatus("PENDING");

        when(documentRepository.findById(10L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);
        when(storeMapper.toDocumentDto(any())).thenReturn(new StoreDocumentDto(10L, 1L, "RUT", "http://doc.pdf", "APPROVED", null, null, null));

        StoreDocumentDto result = storeService.reviewDocument(1L, 10L, new ReviewDocumentCommand("APPROVED", null));
        assertNotNull(result);
        assertEquals("APPROVED", result.status());
        verify(notificationFacade, times(1)).createNotification(argThat(cmd ->
                cmd.storeId().equals(1L) && "DOCUMENT_APPROVED".equals(cmd.type())));
    }

    @Test
    void reviewDocument_Rejected_NotifiesStore() {
        StoreDocumentEntity doc = new StoreDocumentEntity();
        doc.setId(11L);
        doc.setStoreId(1L);
        doc.setDocumentType("RUT");
        doc.setStatus("PENDING");

        when(documentRepository.findById(11L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);
        when(storeMapper.toDocumentDto(any())).thenReturn(new StoreDocumentDto(11L, 1L, "RUT", "http://doc.pdf", "REJECTED", "Ilegible", null, null));

        storeService.reviewDocument(1L, 11L, new ReviewDocumentCommand("REJECTED", "Ilegible"));

        verify(notificationFacade, times(1)).createNotification(argThat(cmd ->
                cmd.storeId().equals(1L) && "DOCUMENT_REJECTED".equals(cmd.type()) && cmd.message().contains("Ilegible")));
    }

    @Test
    void updateOwnStore_Success_UpdatesSafeFields() {
        UserDto owner = new UserDto(5L, "Carlos", "carlos", "carlos@test.com", "ADMINISTRATOR", true, 1L, true);
        when(userFacade.findByUsername("carlos")).thenReturn(Optional.of(owner));

        StoreEntity entity = new StoreEntity();
        entity.setId(1L);
        entity.setName("Tienda Vieja");
        entity.setEmail("tienda1@test.com");
        entity.setStatus("ACTIVE");
        when(storeRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(storeRepository.save(any(StoreEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storeMapper.toDto(any(), any())).thenAnswer(inv -> {
            StoreEntity e = inv.getArgument(0);
            return new StoreDto(e.getId(), e.getName(), null, null, e.getPhone(), e.getEmail(), e.getWebsite(), e.getAddress(), e.getTaxId(), e.getStatus(), e.isEmailVerified(), null, null, "BASIC");
        });

        UpdateOwnStoreCommand command = new UpdateOwnStoreCommand("Tienda Nueva", "3009999999", "https://nueva.com", "Calle Nueva 1", "900999999-1");
        StoreDto result = storeService.updateOwnStore("carlos", command);

        assertEquals("Tienda Nueva", result.name());
        assertEquals("3009999999", result.phone());
        assertEquals("900999999-1", result.taxId());
        // email y status no deben cambiar via esta ruta
        assertEquals("tienda1@test.com", result.email());
        assertEquals("ACTIVE", result.status());
    }

    @Test
    void updateOwnStore_OwnerHasNoStore_ThrowsException() {
        UserDto owner = new UserDto(5L, "Carlos", "carlos", "carlos@test.com", "ADMINISTRATOR", true, null, true);
        when(userFacade.findByUsername("carlos")).thenReturn(Optional.of(owner));

        UpdateOwnStoreCommand command = new UpdateOwnStoreCommand("Tienda", null, null, null, null);
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> storeService.updateOwnStore("carlos", command));
        verify(storeRepository, never()).save(any());
    }

    @Test
    void getOwnDocuments_Success_ReturnsDocumentsOfOwnStore() {
        UserDto owner = new UserDto(5L, "Carlos", "carlos", "carlos@test.com", "ADMINISTRATOR", true, 1L, true);
        when(userFacade.findByUsername("carlos")).thenReturn(Optional.of(owner));
        when(storeRepository.existsById(1L)).thenReturn(true);

        StoreDocumentEntity doc = new StoreDocumentEntity();
        doc.setId(10L);
        doc.setStoreId(1L);
        when(documentRepository.findByStoreId(1L)).thenReturn(List.of(doc));
        when(storeMapper.toDocumentDto(doc)).thenReturn(new StoreDocumentDto(10L, 1L, "RUT", "http://doc.pdf", "PENDING", null, null, null));

        List<StoreDocumentDto> result = storeService.getOwnDocuments("carlos");

        assertEquals(1, result.size());
        assertEquals("RUT", result.get(0).documentType());
    }

    @Test
    void uploadOwnDocument_Success_CreatesPendingDocument() {
        UserDto owner = new UserDto(5L, "Carlos", "carlos", "carlos@test.com", "ADMINISTRATOR", true, 1L, true);
        when(userFacade.findByUsername("carlos")).thenReturn(Optional.of(owner));
        MockMultipartFile file = new MockMultipartFile("file", "rut.pdf", "application/pdf", "contenido".getBytes());
        when(storageFacade.uploadFile(eq(file), eq("documentos")))
                .thenReturn(new StorageUploadResult("documentos/uuid.pdf", "https://cdn.test/rut.pdf", "rut.pdf", 9L, "application/pdf"));
        when(documentRepository.save(any(StoreDocumentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storeMapper.toDocumentDto(any())).thenAnswer(inv -> {
            StoreDocumentEntity e = inv.getArgument(0);
            return new StoreDocumentDto(1L, e.getStoreId(), e.getDocumentType(), e.getDocumentUrl(), e.getStatus(), null, null, null);
        });

        StoreDocumentDto result = storeService.uploadOwnDocument("carlos", "rut", file);

        assertNotNull(result);
        assertEquals("RUT", result.documentType());
        assertEquals("PENDING", result.status());
        assertEquals("https://cdn.test/rut.pdf", result.documentUrl());
    }

    @Test
    void uploadOwnDocument_InvalidType_ThrowsException() {
        UserDto owner = new UserDto(5L, "Carlos", "carlos", "carlos@test.com", "ADMINISTRATOR", true, 1L, true);
        when(userFacade.findByUsername("carlos")).thenReturn(Optional.of(owner));
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "x".getBytes());

        assertThrows(IllegalArgumentException.class, () -> storeService.uploadOwnDocument("carlos", "NOT_A_TYPE", file));
        verify(storageFacade, never()).uploadFile(any(), any());
    }

    @Test
    void uploadOwnDocument_OwnerHasNoStore_ThrowsException() {
        UserDto owner = new UserDto(5L, "Carlos", "carlos", "carlos@test.com", "ADMINISTRATOR", true, null, true);
        when(userFacade.findByUsername("carlos")).thenReturn(Optional.of(owner));
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "x".getBytes());

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> storeService.uploadOwnDocument("carlos", "RUT", file));
    }
}