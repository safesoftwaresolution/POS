package com.sciencebot.pos.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageFacade {

    /**
     * Sube un archivo Multipart a Cloudflare R2 dentro de una carpeta especifica.
     *
     * @param file   Archivo a subir
     * @param folder Carpeta destino (ej: "products", "stores", "invoices")
     * @return Resultado con clave, url publica y metadatos
     */
    StorageUploadResult uploadFile(MultipartFile file, String folder);

    /**
     * Sube un array de bytes a Cloudflare R2.
     *
     * @param data        Contenido binario
     * @param filename    Nombre original del archivo
     * @param contentType Tipo MIME
     * @param folder      Carpeta destino
     * @return Resultado con clave, url publica y metadatos
     */
    StorageUploadResult uploadBytes(byte[] data, String filename, String contentType, String folder);

    /**
     * Elimina un archivo de Cloudflare R2 por su clave.
     *
     * @param key Clave del objeto en el bucket (ej: "products/uuid.jpg")
     */
    void deleteFile(String key);
}
