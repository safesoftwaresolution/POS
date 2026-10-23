package com.sciencebot.pos.stores;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface StoreFacade {
    StoreMetricsDto getMetrics();
    Page<StoreDto> searchStores(String status, String q, Pageable pageable);
    StoreDto createStore(CreateStoreCommand command);
    StoreDto updateStore(Long id, UpdateStoreCommand command);
    StoreDto changeStatus(Long id, String newStatus);
    StoreDto verifyEmail(Long id);
    StoreDto getById(Long id);
    /** Auto-registro self-service: crea el local del ADMINISTRATOR autenticado (que aun no tiene uno). */
    StoreDto registerOwnStore(String ownerUsername, CreateStoreCommand command);
    /** Local del ADMINISTRATOR autenticado. */
    StoreDto getOwnStore(String ownerUsername);
    /** Actualiza los datos "seguros" del local del ADMINISTRATOR autenticado (no email/status). */
    StoreDto updateOwnStore(String ownerUsername, UpdateOwnStoreCommand command);
    /** Documentos KYC subidos por el local del ADMINISTRATOR autenticado. */
    List<StoreDocumentDto> getOwnDocuments(String ownerUsername);
    /** Sube un nuevo documento KYC (queda en PENDING) para el local del ADMINISTRATOR autenticado. */
    StoreDocumentDto uploadOwnDocument(String ownerUsername, String documentType, MultipartFile file);
    /** Rechaza un local pendiente con un motivo obligatorio. */
    StoreDto rejectStore(Long id, String reason);
    /** Elimina definitivamente un local RECHAZADO que nunca operó, junto con su dueño y documentos. */
    void deleteRejectedStore(Long id);
    /** Asigna un ADMINISTRATOR sin local a un local sin administrador activo (solo SUPER_ADMIN; auditado). */
    StoreDto assignOwner(Long storeId, Long userId);
    /** Historial de solo lectura de las asignaciones de usuarios a locales, de la más reciente a la más antigua. */
    Page<StoreUserAuditDto> getOwnerAssignments(Pageable pageable);
    /** Planes disponibles, del más básico al más alto. */
    List<PlanDto> listPlans();
    /** Cambia el plan de un local (SUPER_ADMIN; auditado). No borra usuarios si el nuevo tope es menor. */
    StoreDto changePlan(Long storeId, String planCode);
    /** Historial de solo lectura de cambios de estado de locales (registro, aprobación, rechazo...). */
    Page<StoreStatusAuditDto> getStatusAudit(String newStatus, String q, Pageable pageable);
    List<StoreDocumentDto> getDocuments(Long storeId);
    StoreDocumentDto reviewDocument(Long storeId, Long docId, ReviewDocumentCommand command);
    List<StoreCategoryDto> listCategories();
    StoreCategoryDto createCategory(CreateStoreCategoryCommand command);
    StoreCategoryDto updateCategory(Long id, CreateStoreCategoryCommand command);
    void deleteCategory(Long id);
}