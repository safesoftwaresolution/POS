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
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

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

    @Test
    void deleteFileByUrl_OwnKey_DeletesTheObject() {
        storageService.deleteFileByUrl("https://pub-test.r2.dev/imagenes-productos/tienda-1/abc.jpg",
                "imagenes-productos/tienda-1/");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertEquals("imagenes-productos/tienda-1/abc.jpg", captor.getValue().key());
    }

    @Test
    void deleteFileByUrl_LegacyLayout_IsDeletedWhenItsPrefixIsAllowed() {
        storageService.deleteFileByUrl("https://pub-test.r2.dev/tienda-1/imagenes-productos/abc.jpg",
                "imagenes-productos/tienda-1/", "tienda-1/imagenes-productos/");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertEquals("tienda-1/imagenes-productos/abc.jpg", captor.getValue().key());
    }

    @Test
    void deleteFileByUrl_OtherStoreOtherBucketOrTraversal_DoesNothing() {
        String prefix = "imagenes-productos/tienda-1/";
        storageService.deleteFileByUrl("https://pub-test.r2.dev/imagenes-productos/tienda-2/abc.jpg", prefix);
        storageService.deleteFileByUrl("https://otro-sitio.com/imagenes-productos/tienda-1/abc.jpg", prefix);
        storageService.deleteFileByUrl("https://pub-test.r2.dev/imagenes-productos/tienda-1/../tienda-2/x.jpg", prefix);
        storageService.deleteFileByUrl(null, prefix);
        storageService.deleteFileByUrl("https://pub-test.r2.dev/imagenes-productos/tienda-1/abc.jpg", " ");

        verifyNoInteractions(s3Client);
    }

    @Test
    void listFiles_ReadsEveryPage() {
        Instant t = Instant.parse("2026-10-01T00:00:00Z");
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("imagenes-productos/tienda-1/a.jpg").lastModified(t).build())
                        .isTruncated(true).nextContinuationToken("t2").build())
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("imagenes-productos/tienda-1/b.jpg").lastModified(t).build())
                        .isTruncated(false).build());

        List<StoredFile> files = storageService.listFiles("imagenes-productos/");

        assertEquals(2, files.size());
        assertEquals("https://pub-test.r2.dev/imagenes-productos/tienda-1/a.jpg", files.get(0).url());
        assertEquals(t, files.get(1).lastModified());
    }

    @Test
    void listFiles_WithoutCredentials_DoesNotCallR2() {
        properties.setAccessKeyId("");

        assertTrue(storageService.listFiles("imagenes-productos/").isEmpty());
        verifyNoInteractions(s3Client);
    }
}
