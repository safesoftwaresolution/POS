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
import java.util.UUID;

@Service
public class StorageServiceImpl implements StorageFacade {

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
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

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
        return folder.trim().replaceAll("^/+|/+$", "");
    }

    private String buildPublicUrl(String key) {
        String base = properties.getPublicUrl();
        if (base == null || base.isBlank()) {
            return "/" + key;
        }
        return base.replaceAll("/+$", "") + "/" + key;
    }
}
