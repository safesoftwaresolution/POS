package com.sciencebot.pos.stores.internal.controllers;

import com.sciencebot.pos.stores.CreateStoreCommand;
import com.sciencebot.pos.stores.StoreDocumentDto;
import com.sciencebot.pos.stores.StoreDto;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.stores.StoreFacade;
import com.sciencebot.pos.stores.StoreTemplateDto;
import jakarta.persistence.EntityNotFoundException;
import com.sciencebot.pos.stores.UpdateOwnStoreCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stores")
@Tag(name = "Onboarding de Locales", description = "Auto-registro del local por su propio ADMINISTRATOR")
public class StoreOnboardingController {

    private final StoreFacade storeFacade;

    public StoreOnboardingController(StoreFacade storeFacade) {
        this.storeFacade = storeFacade;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Registrar mi local", description = "Crea el local del ADMINISTRATOR autenticado (que aun no tiene uno). Queda en PENDING_VERIFICATION.")
    public ResponseEntity<StoreDto> registerOwnStore(@RequestBody CreateStoreCommand command, Authentication authentication) {
        StoreDto created = storeFacade.registerOwnStore(authentication.getName(), command);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Obtener mi local", description = "Retorna el local del ADMINISTRATOR autenticado.")
    public ResponseEntity<StoreDto> getOwnStore(Authentication authentication) {
        return ResponseEntity.ok(storeFacade.getOwnStore(authentication.getName()));
    }

    @GetMapping("/me/template")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Plantilla de mi local", description = "Plantilla funcional (RETAIL, FOOD, APPOINTMENTS) del local del usuario autenticado, para cualquier rol del local. Se deriva de la categoria del local.")
    public ResponseEntity<StoreTemplateDto> getOwnTemplate() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new EntityNotFoundException("El usuario no pertenece a un local.");
        }
        return ResponseEntity.ok(new StoreTemplateDto(storeFacade.getTemplateForStore(storeId).name()));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Actualizar mi local", description = "Actualiza nombre, teléfono, sitio web, dirección y NIT del local del ADMINISTRATOR autenticado. El correo y el estado de verificación no se pueden cambiar por esta vía.")
    public ResponseEntity<StoreDto> updateOwnStore(@RequestBody UpdateOwnStoreCommand command, Authentication authentication) {
        return ResponseEntity.ok(storeFacade.updateOwnStore(authentication.getName(), command));
    }

    @GetMapping("/me/documents")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Listar mis documentos legales", description = "Documentos KYC subidos por el local del ADMINISTRATOR autenticado.")
    public ResponseEntity<List<StoreDocumentDto>> getOwnDocuments(Authentication authentication) {
        return ResponseEntity.ok(storeFacade.getOwnDocuments(authentication.getName()));
    }

    @PostMapping(value = "/me/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Subir un documento legal", description = "Sube un nuevo documento KYC (RUT, Cámara de Comercio, cédula, certificación bancaria u otro) para el local del ADMINISTRATOR autenticado. Queda en PENDING hasta que el backoffice lo revise.")
    public ResponseEntity<StoreDocumentDto> uploadOwnDocument(
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        StoreDocumentDto uploaded = storeFacade.uploadOwnDocument(authentication.getName(), documentType, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
    }
}
