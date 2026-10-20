package com.sciencebot.pos.stores.internal.services;

import com.sciencebot.pos.stores.*;
import com.sciencebot.pos.stores.internal.entities.StoreCategoryEntity;
import com.sciencebot.pos.stores.internal.entities.StoreDocumentEntity;
import com.sciencebot.pos.stores.internal.entities.StoreEntity;
import com.sciencebot.pos.stores.internal.mappers.StoreMapper;
import com.sciencebot.pos.stores.internal.repositories.StoreCategoryRepository;
import com.sciencebot.pos.stores.internal.repositories.StoreDocumentRepository;
import com.sciencebot.pos.stores.internal.repositories.StoreRepository;
import com.sciencebot.pos.notifications.CreateNotificationCommand;
import com.sciencebot.pos.notifications.NotificationFacade;
import com.sciencebot.pos.storage.StorageFacade;
import com.sciencebot.pos.storage.StorageUploadResult;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class StoreServiceImpl implements StoreFacade {

    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "INACTIVE", "PENDING_VERIFICATION", "SUSPENDED", "REJECTED");
    private static final Set<String> VALID_DOCUMENT_TYPES = Set.of("RUT", "COMMERCE_CHAMBER", "ID_CARD", "BANK_CERTIFICATE", "OTHER");

    private final StoreRepository storeRepository;
    private final StoreCategoryRepository categoryRepository;
    private final StoreDocumentRepository documentRepository;
    private final StoreMapper storeMapper;
    private final UserFacade userFacade;
    private final StorageFacade storageFacade;
    private final NotificationFacade notificationFacade;

    public StoreServiceImpl(StoreRepository storeRepository,
                            StoreCategoryRepository categoryRepository,
                            StoreDocumentRepository documentRepository,
                            StoreMapper storeMapper,
                            UserFacade userFacade,
                            StorageFacade storageFacade,
                            NotificationFacade notificationFacade) {
        this.storeRepository = storeRepository;
        this.categoryRepository = categoryRepository;
        this.documentRepository = documentRepository;
        this.storeMapper = storeMapper;
        this.userFacade = userFacade;
        this.storageFacade = storageFacade;
        this.notificationFacade = notificationFacade;
    }

    @Override
    public StoreMetricsDto getMetrics() {
        long total    = storeRepository.count();
        long active   = storeRepository.countByStatus("ACTIVE");
        long inactive = storeRepository.countByStatus("INACTIVE");
        long pending  = storeRepository.countByStatus("PENDING_VERIFICATION");
        long suspended = storeRepository.countByStatus("SUSPENDED");
        long verified = storeRepository.countByEmailVerifiedTrue();
        return new StoreMetricsDto(total, active, inactive, pending, suspended, verified);
    }

    @Override
    public Page<StoreDto> searchStores(String status, String q, Pageable pageable) {
        String statusParam = (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) ? null : status.toUpperCase();
        String qParam = (q == null || q.isBlank()) ? null : q.trim();
        return storeRepository.searchStores(statusParam, qParam, pageable)
                .map(s -> storeMapper.toDto(s, resolveCategoryName(s.getStoreCategoryId())));
    }

    @Override
    public StoreDto getById(Long id) {
        StoreEntity entity = storeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Local no encontrado con ID: " + id));
        return storeMapper.toDto(entity, resolveCategoryName(entity.getStoreCategoryId()));
    }

    @Override
    @Transactional
    public StoreDto createStore(CreateStoreCommand command) {
        if (storeRepository.existsByEmail(command.email())) {
            throw new IllegalArgumentException("El correo ya esta registrado para otro local.");
        }
        StoreEntity entity = new StoreEntity();
        entity.setName(command.name());
        entity.setStoreCategoryId(command.storeCategoryId());
        entity.setPhone(command.phone());
        entity.setEmail(command.email());
        entity.setWebsite(command.website());
        entity.setAddress(command.address());
        entity.setTaxId(command.taxId());
        entity.setStatus("PENDING_VERIFICATION");
        entity.setEmailVerified(false);
        StoreEntity saved = storeRepository.save(entity);
        return storeMapper.toDto(saved, resolveCategoryName(saved.getStoreCategoryId()));
    }

    @Override
    @Transactional
    public StoreDto updateStore(Long id, UpdateStoreCommand command) {
        StoreEntity entity = storeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Local no encontrado con ID: " + id));
        entity.setName(command.name());
        entity.setStoreCategoryId(command.storeCategoryId());
        entity.setPhone(command.phone());
        entity.setWebsite(command.website());
        entity.setAddress(command.address());
        entity.setTaxId(command.taxId());
        if (!entity.getEmail().equalsIgnoreCase(command.email()) && storeRepository.existsByEmail(command.email())) {
            throw new IllegalArgumentException("El correo ya esta registrado para otro local.");
        }
        entity.setEmail(command.email());
        return storeMapper.toDto(storeRepository.save(entity), resolveCategoryName(entity.getStoreCategoryId()));
    }

    @Override
    @Transactional
    public StoreDto changeStatus(Long id, String newStatus) {
        if (!VALID_STATUSES.contains(newStatus.toUpperCase())) {
            throw new IllegalArgumentException("Estado invalido: " + newStatus);
        }
        StoreEntity entity = storeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Local no encontrado con ID: " + id));
        entity.setStatus(newStatus.toUpperCase());
        return storeMapper.toDto(storeRepository.save(entity), resolveCategoryName(entity.getStoreCategoryId()));
    }

    @Override
    @Transactional
    public StoreDto verifyEmail(Long id) {
        StoreEntity entity = storeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Local no encontrado con ID: " + id));
        entity.setEmailVerified(true);
        return storeMapper.toDto(storeRepository.save(entity), resolveCategoryName(entity.getStoreCategoryId()));
    }

    @Override
    @Transactional
    public StoreDto registerOwnStore(String ownerUsername, CreateStoreCommand command) {
        UserDto owner = userFacade.findByUsername(ownerUsername)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + ownerUsername));
        if (!"ADMINISTRATOR".equals(owner.role())) {
            throw new AccessDeniedException("Solo un ADMINISTRATOR puede registrar un local.");
        }
        if (owner.storeId() != null) {
            throw new IllegalArgumentException("Ya tienes un local registrado.");
        }

        StoreDto created = createStore(command);
        userFacade.assignStore(owner.id(), created.id());
        return created;
    }

    @Override
    public StoreDto getOwnStore(String ownerUsername) {
        UserDto owner = userFacade.findByUsername(ownerUsername)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + ownerUsername));
        if (owner.storeId() == null) {
            throw new EntityNotFoundException("Aun no has registrado un local.");
        }
        return getById(owner.storeId());
    }

    @Override
    @Transactional
    public StoreDto updateOwnStore(String ownerUsername, UpdateOwnStoreCommand command) {
        Long storeId = requireOwnStoreId(ownerUsername);
        StoreEntity entity = storeRepository.findById(storeId)
                .orElseThrow(() -> new EntityNotFoundException("Local no encontrado con ID: " + storeId));

        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("El nombre del local es obligatorio");
        }
        entity.setName(command.name().trim());
        entity.setPhone(command.phone());
        entity.setWebsite(command.website());
        entity.setAddress(command.address());
        entity.setTaxId(command.taxId());
        // Deliberadamente no se tocan email/status/emailVerified/storeCategoryId aqui: siguen
        // bajo control del backoffice (ver UpdateOwnStoreCommand).
        return storeMapper.toDto(storeRepository.save(entity), resolveCategoryName(entity.getStoreCategoryId()));
    }

    @Override
    public List<StoreDocumentDto> getOwnDocuments(String ownerUsername) {
        return getDocuments(requireOwnStoreId(ownerUsername));
    }

    @Override
    @Transactional
    public StoreDocumentDto uploadOwnDocument(String ownerUsername, String documentType, MultipartFile file) {
        Long storeId = requireOwnStoreId(ownerUsername);

        String type = documentType == null ? "" : documentType.trim().toUpperCase();
        if (!VALID_DOCUMENT_TYPES.contains(type)) {
            throw new IllegalArgumentException("Tipo de documento inválido. Use uno de: " + VALID_DOCUMENT_TYPES);
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debes adjuntar un archivo.");
        }

        // "documentos" es el nombre exacto de la carpeta creada en el bucket de Cloudflare R2.
        StorageUploadResult uploaded = storageFacade.uploadFile(file, "documentos");

        StoreDocumentEntity doc = new StoreDocumentEntity();
        doc.setStoreId(storeId);
        doc.setDocumentType(type);
        doc.setDocumentUrl(uploaded.url());
        doc.setStatus("PENDING");
        return storeMapper.toDocumentDto(documentRepository.save(doc));
    }

    /** Resuelve el storeId del ADMINISTRATOR autenticado, exigiendo que ya tenga un local registrado. */
    private Long requireOwnStoreId(String ownerUsername) {
        UserDto owner = userFacade.findByUsername(ownerUsername)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + ownerUsername));
        if (owner.storeId() == null) {
            throw new EntityNotFoundException("Aun no has registrado un local.");
        }
        return owner.storeId();
    }

    @Override
    @Transactional
    public StoreDto rejectStore(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Se requiere motivo de rechazo.");
        }
        StoreEntity entity = storeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Local no encontrado con ID: " + id));
        entity.setStatus("REJECTED");
        entity.setRejectionReason(reason);
        return storeMapper.toDto(storeRepository.save(entity), resolveCategoryName(entity.getStoreCategoryId()));
    }

    @Override
    public List<StoreDocumentDto> getDocuments(Long storeId) {
        if (!storeRepository.existsById(storeId)) {
            throw new EntityNotFoundException("Local no encontrado con ID: " + storeId);
        }
        return documentRepository.findByStoreId(storeId).stream()
                .map(storeMapper::toDocumentDto).toList();
    }

    @Override
    @Transactional
    public StoreDocumentDto reviewDocument(Long storeId, Long docId, ReviewDocumentCommand command) {
        StoreDocumentEntity doc = documentRepository.findById(docId)
                .orElseThrow(() -> new EntityNotFoundException("Documento no encontrado con ID: " + docId));
        if (!doc.getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("El documento no pertenece al local indicado.");
        }
        String newStatus = command.status().toUpperCase();
        if (!Set.of("APPROVED", "REJECTED").contains(newStatus)) {
            throw new IllegalArgumentException("Estado de documento invalido. Use APPROVED o REJECTED.");
        }
        if ("REJECTED".equals(newStatus) && (command.rejectionReason() == null || command.rejectionReason().isBlank())) {
            throw new IllegalArgumentException("Se requiere motivo de rechazo.");
        }
        doc.setStatus(newStatus);
        doc.setRejectionReason(command.rejectionReason());
        doc.setVerifiedAt(LocalDateTime.now());
        StoreDocumentDto reviewed = storeMapper.toDocumentDto(documentRepository.save(doc));

        boolean approved = "APPROVED".equals(newStatus);
        notificationFacade.createNotification(new CreateNotificationCommand(
                storeId,
                approved ? "DOCUMENT_APPROVED" : "DOCUMENT_REJECTED",
                approved ? "Documento aprobado" : "Documento rechazado",
                approved
                        ? "Tu " + documentTypeLabel(doc.getDocumentType()) + " fue aprobado."
                        : "Tu " + documentTypeLabel(doc.getDocumentType()) + " fue rechazado"
                                + (command.rejectionReason() != null ? ": " + command.rejectionReason() : "."),
                "/documents"
        ));

        return reviewed;
    }

    private static String documentTypeLabel(String documentType) {
        if (documentType == null) {
            return "documento";
        }
        return switch (documentType) {
            case "RUT" -> "RUT";
            case "COMMERCE_CHAMBER" -> "Cámara de Comercio";
            case "ID_CARD" -> "Cédula";
            case "BANK_CERTIFICATE" -> "Certificación Bancaria";
            default -> "documento";
        };
    }

    @Override
    public List<StoreCategoryDto> listCategories() {
        return categoryRepository.findByActiveTrue().stream()
                .map(storeMapper::toCategoryDto).toList();
    }

    @Override
    @Transactional
    public StoreCategoryDto createCategory(CreateStoreCategoryCommand command) {
        if (categoryRepository.existsByNameIgnoreCase(command.name())) {
            throw new IllegalArgumentException("Ya existe una categoria con ese nombre.");
        }
        StoreCategoryEntity entity = new StoreCategoryEntity();
        entity.setName(command.name());
        entity.setDescription(command.description());
        entity.setActive(true);
        return storeMapper.toCategoryDto(categoryRepository.save(entity));
    }

    @Override
    @Transactional
    public StoreCategoryDto updateCategory(Long id, CreateStoreCategoryCommand command) {
        StoreCategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Categoria no encontrada con ID: " + id));
        entity.setName(command.name());
        entity.setDescription(command.description());
        return storeMapper.toCategoryDto(categoryRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        StoreCategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Categoria no encontrada con ID: " + id));
        entity.setActive(false);
        categoryRepository.save(entity);
    }

    private String resolveCategoryName(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId).map(StoreCategoryEntity::getName).orElse(null);
    }
}