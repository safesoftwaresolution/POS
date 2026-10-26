package com.sciencebot.pos.storage.internal.services;

import com.sciencebot.pos.storage.StorageFacade;
import com.sciencebot.pos.storage.StorageUploadResult;
import com.sciencebot.pos.storage.internal.config.R2StorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class StorageServiceImpl implements StorageFacade {

    private static final Map<String, String> ALLOWED_UPLOAD_TYPES = Map.of(
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png", "image/png",
            ".webp", "image/webp",
            ".gif", "image/gif",
            ".pdf", "application/pdf");

    private final S3Client s3Client;
    private final R2StorageProperties properties;

    public StorageServiceImpl(S3Client s3Client, R2StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public StorageUploadResult uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo a subir no puede estar vacio");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String contentType = file.getContentType();
        validateUploadType(extension, contentType);

        String sanitizedFolder = sanitizeFolder(folder);
        String key = (sanitizedFolder.isEmpty() ? "" : sanitizedFolder + "/") + UUID.randomUUID() + extension;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(properties.getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String publicUrl = buildPublicUrl(key);
            return new StorageUploadResult(
                    key,
                    publicUrl,
                    originalFilename != null ? originalFilename : key,
                    file.getSize(),
                    contentType
            );
        } catch (IOException e) {
            throw new IllegalStateException("Error al leer el archivo para subir a Cloudflare R2: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Error al subir archivo a Cloudflare R2: " + e.getMessage(), e);
        }
    }

    @Override
    public StorageUploadResult uploadBytes(byte[] data, String filename, String contentType, String folder) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Los datos a subir no pueden estar vacios");
        }

        String extension = getFileExtension(filename);
        String mimeType = (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";
        String sanitizedFolder = sanitizeFolder(folder);
        String key = (sanitizedFolder.isEmpty() ? "" : sanitizedFolder + "/") + UUID.randomUUID() + extension;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(properties.getBucketName())
                    .key(key)
                    .contentType(mimeType)
                    .contentLength((long) data.length)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(data));

            String publicUrl = buildPublicUrl(key);
            return new StorageUploadResult(
                    key,
                    publicUrl,
                    filename != null ? filename : key,
                    data.length,
                    mimeType
            );
        } catch (Exception e) {
            throw new IllegalStateException("Error al subir archivo a Cloudflare R2: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(properties.getBucketName())
                    .key(key.trim())
                    .build();

            s3Client.deleteObject(deleteRequest);
        } catch (Exception e) {
            throw new IllegalStateException("Error al eliminar archivo de Cloudflare R2: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFileByUrl(String publicUrl, String keyPrefix) {
        if (publicUrl == null || publicUrl.isBlank() || keyPrefix == null || keyPrefix.isBlank()) {
            return;
        }
        String base = properties.getPublicUrl() == null ? "" : properties.getPublicUrl().replaceAll("/+$", "");
        String url = publicUrl.trim();
        if (!url.startsWith(base + "/")) {
            return;
        }
        String key = url.substring(base.length() + 1);
        if (key.contains("..") || !key.startsWith(keyPrefix)) {
            return;
        }
        deleteFile(key);
    }

    private String getFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".")).toLowerCase();
        }
        return "";
    }

    private String sanitizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "";
        }
        String clean = folder.trim().replaceAll("^/+|/+$", "");
        if (!clean.matches("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*")) {
            throw new IllegalArgumentException("Carpeta de destino invalida");
        }
        return clean;
    }

    /**
     * Solo imagenes rasterizadas y PDF: se sirven desde un dominio publico, asi que .html/.svg
     * permitirian XSS almacenado. Extension y Content-Type deben ser coherentes entre si.
     */
    private void validateUploadType(String extension, String contentType) {
        String mime = contentType == null ? "" : contentType.toLowerCase().split(";")[0].trim();
        String expected = ALLOWED_UPLOAD_TYPES.get(extension);
        if (expected == null || !(expected.equals(mime) || ("image/jpeg".equals(expected) && "image/jpg".equals(mime)))) {
            throw new IllegalArgumentException("Tipo de archivo no permitido. Formatos validos: jpg, png, webp, gif, pdf");
        }
    }

    private String buildPublicUrl(String key) {
        String base = properties.getPublicUrl();
        if (base == null || base.isBlank()) {
            return "/" + key;
        }
        return base.replaceAll("/+$", "") + "/" + key;
    }
}
