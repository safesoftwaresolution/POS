package com.sciencebot.pos.storage.internal.controllers;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.storage.StorageFacade;
import com.sciencebot.pos.storage.StorageUploadResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/storage")
@Tag(name = "Almacenamiento (Storage)", description = "Operaciones de carga y gestion de archivos en Cloudflare R2")
public class StorageController {

    /** Carpetas que el cliente puede pedir; cualquier otra se rechaza (no hay rutas libres). */
    static final Set<String> ALLOWED_FOLDERS = Set.of("imagenes-productos", "imagenes-logos");

    private final StorageFacade storageFacade;

    public StorageController(StorageFacade storageFacade) {
        this.storageFacade = storageFacade;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(summary = "Subir archivo a Cloudflare R2",
            description = "Sube una imagen o PDF a la carpeta del local actual y retorna la URL publica y metadatos. "
                    + "Solo ADMINISTRATOR y SUPERVISOR.")
    public ResponseEntity<StorageUploadResult> uploadFile(
            @Parameter(description = "Archivo binario a subir (jpg, png, webp, gif o pdf)")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Carpeta destino: imagenes-productos o imagenes-logos")
            @RequestParam(value = "folder", defaultValue = "imagenes-productos") String folder
    ) {
        if (!ALLOWED_FOLDERS.contains(folder)) {
            throw new IllegalArgumentException("Carpeta no permitida. Use: " + String.join(", ", ALLOWED_FOLDERS));
        }
        StorageUploadResult result = storageFacade.uploadFile(file, storeKeyPrefix() + folder);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/files")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(summary = "Eliminar archivo de Cloudflare R2",
            description = "Elimina un archivo del bucket. Solo se pueden borrar claves del propio local. "
                    + "Solo ADMINISTRATOR y SUPERVISOR.")
    public ResponseEntity<Map<String, String>> deleteFile(
            @Parameter(description = "Clave del archivo en el bucket (ej: tienda-1/imagenes-productos/uuid.jpg)")
            @RequestParam("key") String key
    ) {
        String prefix = storeKeyPrefix();
        if (key == null || key.contains("..") || !key.startsWith(prefix)) {
            throw new AccessDeniedException("El archivo no pertenece a este local");
        }
        storageFacade.deleteFile(key);
        return ResponseEntity.ok(Map.of("message", "Archivo eliminado correctamente"));
    }

    private static String storeKeyPrefix() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new AccessDeniedException("Se requiere un local para gestionar archivos");
        }
        return "tienda-" + storeId + "/";
    }
}
