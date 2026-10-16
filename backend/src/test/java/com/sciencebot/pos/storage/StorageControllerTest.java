package com.sciencebot.pos.storage;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.storage.internal.controllers.StorageController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageControllerTest {

    @Mock
    private StorageFacade storageFacade;

    @InjectMocks
    private StorageController storageController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setStoreId(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void uploadFile_PrefixesFolderWithCallerStore() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "content".getBytes());
        StorageUploadResult expectedResult = new StorageUploadResult(
                "tienda-7/imagenes-productos/uuid.jpg",
                "https://pub-test.r2.dev/tienda-7/imagenes-productos/uuid.jpg",
                "test.jpg", 7L, "image/jpeg");
        when(storageFacade.uploadFile(file, "tienda-7/imagenes-productos")).thenReturn(expectedResult);

        ResponseEntity<StorageUploadResult> response = storageController.uploadFile(file, "imagenes-productos");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expectedResult, response.getBody());
        verify(storageFacade).uploadFile(file, "tienda-7/imagenes-productos");
    }

    @Test
    void uploadFile_FolderOutsideWhitelist_IsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", "c".getBytes());

        assertThrows(IllegalArgumentException.class, () -> storageController.uploadFile(file, "../documentos"));
        assertThrows(IllegalArgumentException.class, () -> storageController.uploadFile(file, "documentos"));
        verifyNoInteractions(storageFacade);
    }

    @Test
    void uploadFile_WithoutStoreInContext_IsDenied() {
        TenantContext.clear();
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", "c".getBytes());

        assertThrows(AccessDeniedException.class, () -> storageController.uploadFile(file, "imagenes-logos"));
        verifyNoInteractions(storageFacade);
    }

    @Test
    void deleteFile_OwnKey_CallsFacade() {
        ResponseEntity<Map<String, String>> response =
                storageController.deleteFile("tienda-7/imagenes-productos/uuid.jpg");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Archivo eliminado correctamente", response.getBody().get("message"));
        verify(storageFacade).deleteFile("tienda-7/imagenes-productos/uuid.jpg");
    }

    @Test
    void deleteFile_OtherStoreOrTraversalKey_IsDenied() {
        assertThrows(AccessDeniedException.class, () -> storageController.deleteFile("tienda-8/imagenes-productos/uuid.jpg"));
        assertThrows(AccessDeniedException.class, () -> storageController.deleteFile("documentos/rut.pdf"));
        assertThrows(AccessDeniedException.class, () -> storageController.deleteFile("tienda-7/../tienda-8/uuid.jpg"));
        verifyNoInteractions(storageFacade);
    }

    @Test
    void endpoints_RequireAdministratorOrSupervisor() throws NoSuchMethodException {
        PreAuthorize upload = StorageController.class
                .getMethod("uploadFile", org.springframework.web.multipart.MultipartFile.class, String.class)
                .getAnnotation(PreAuthorize.class);
        PreAuthorize delete = StorageController.class
                .getMethod("deleteFile", String.class)
                .getAnnotation(PreAuthorize.class);

        assertNotNull(upload);
        assertNotNull(delete);
        assertEquals("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')", upload.value());
        assertEquals("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')", delete.value());
    }
}
