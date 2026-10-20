package com.sciencebot.pos.storage;

import com.sciencebot.pos.storage.internal.config.R2StorageProperties;
import com.sciencebot.pos.storage.internal.services.StorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorageServiceImplTest {

    @Mock
    private S3Client s3Client;

    private R2StorageProperties properties;
    private StorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new R2StorageProperties();
        properties.setBucketName("test-bucket");
        properties.setPublicUrl("https://pub-test.r2.dev");
        properties.setAccountId("test-account");
        properties.setAccessKeyId("test-access");
        properties.setSecretAccessKey("test-secret");

        storageService = new StorageServiceImpl(s3Client, properties);
    }

    @Test
    void uploadFile_Success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "arroz_diana.jpg",
                "image/jpeg",
                "fake image content".getBytes()
        );

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(null);

        StorageUploadResult result = storageService.uploadFile(file, "products");

        assertNotNull(result);
        assertNotNull(result.key());
        assertTrue(result.key().startsWith("products/"));
        assertTrue(result.key().endsWith(".jpg"));
        assertEquals("https://pub-test.r2.dev/" + result.key(), result.url());
        assertEquals("arroz_diana.jpg", result.originalFilename());
        assertEquals("image/jpeg", result.contentType());
        assertEquals(file.getSize(), result.sizeBytes());

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest captured = requestCaptor.getValue();
        assertEquals("test-bucket", captured.bucket());
        assertEquals(result.key(), captured.key());
        assertEquals("image/jpeg", captured.contentType());
    }

    @Test
    void uploadFile_NullOrEmptyFile_ThrowsIllegalArgumentException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> storageService.uploadFile(null, "products"));
        assertThrows(IllegalArgumentException.class, () -> storageService.uploadFile(emptyFile, "products"));
        verifyNoInteractions(s3Client);
    }

    @Test
    void uploadBytes_Success() {
        byte[] data = "test invoice bytes".getBytes();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(null);

        StorageUploadResult result = storageService.uploadBytes(data, "factura.pdf", "application/pdf", "invoices");

        assertNotNull(result);
        assertNotNull(result.key());
        assertTrue(result.key().startsWith("invoices/"));
        assertTrue(result.key().endsWith(".pdf"));
        assertEquals("https://pub-test.r2.dev/" + result.key(), result.url());
        assertEquals("factura.pdf", result.originalFilename());
        assertEquals("application/pdf", result.contentType());
        assertEquals(data.length, result.sizeBytes());

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadBytes_NullOrEmpty_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> storageService.uploadBytes(null, "test.png", "image/png", "products"));
        assertThrows(IllegalArgumentException.class, () -> storageService.uploadBytes(new byte[0], "test.png", "image/png", "products"));
        verifyNoInteractions(s3Client);
    }

    @Test
    void deleteFile_Success() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(null);

        storageService.deleteFile("products/123-abc.jpg");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals("products/123-abc.jpg", captor.getValue().key());
    }

    @Test
    void deleteFile_BlankKey_DoesNothing() {
        storageService.deleteFile(null);
        storageService.deleteFile("   ");

        verifyNoInteractions(s3Client);
    }
}
