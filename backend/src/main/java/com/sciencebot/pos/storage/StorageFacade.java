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

    /**
     * Elimina un archivo a partir de su URL publica, solo si su clave empieza por alguno de los
     * {@code keyPrefixes} (ej: "imagenes-productos/tienda-1/"). Si la URL no es de este bucket o queda
     * fuera de los prefijos, no hace nada: nunca borra objetos ajenos.
     *
     * @param publicUrl URL publica devuelta al subir el archivo
     * @param keyPrefixes Prefijos de clave permitidos; el objeto debe cumplir al menos uno para poder borrarse
     */
    void deleteFileByUrl(String publicUrl, String... keyPrefixes);

    /**
     * Lista todos los objetos cuya clave empieza por {@code prefix} (recorre todas las paginas de R2).
     * Si el almacenamiento no esta configurado devuelve una lista vacia en vez de intentar conectarse.
     *
     * @param prefix Prefijo de clave (ej: "imagenes-productos/")
     */
    java.util.List<StoredFile> listFiles(String prefix);
}
