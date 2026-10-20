package com.sciencebot.pos.storage.internal.controllers;

import com.sciencebot.pos.storage.StorageFacade;
import com.sciencebot.pos.storage.StorageUploadResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/storage")
@Tag(name = "Almacenamiento (Storage)", description = "Operaciones de carga y gestion de archivos en Cloudflare R2")
public class StorageController {

    private final StorageFacade storageFacade;

    public StorageController(StorageFacade storageFacade) {
        this.storageFacade = storageFacade;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir archivo a Cloudflare R2", description = "Sube un archivo a Cloudflare R2 y retorna la URL publica y metadatos.")
    public ResponseEntity<StorageUploadResult> uploadFile(
            @Parameter(description = "Archivo binario a subir")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Carpeta destino dentro del bucket (ej: products, stores, receipts)")
            @RequestParam(value = "folder", defaultValue = "products") String folder
    ) {
        StorageUploadResult result = storageFacade.uploadFile(file, folder);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/files")
    @Operation(summary = "Eliminar archivo de Cloudflare R2", description = "Elimina un archivo del bucket dada su clave/path.")
    public ResponseEntity<Map<String, String>> deleteFile(
            @Parameter(description = "Clave del archivo en el bucket (ej: products/uuid.jpg)")
            @RequestParam("key") String key
    ) {
        storageFacade.deleteFile(key);
        return ResponseEntity.ok(Map.of("message", "Archivo eliminado correctamente"));
    }
}
