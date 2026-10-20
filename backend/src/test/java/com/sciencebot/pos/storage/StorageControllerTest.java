package com.sciencebot.pos.storage;

import com.sciencebot.pos.storage.internal.controllers.StorageController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

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
    }

    @Test
    void uploadFile_ReturnsUploadResult() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        StorageUploadResult expectedResult = new StorageUploadResult(
                "products/uuid.jpg",
                "https://pub-test.r2.dev/products/uuid.jpg",
                "test.jpg",
                7L,
                "image/jpeg"
        );

        when(storageFacade.uploadFile(file, "products")).thenReturn(expectedResult);

        ResponseEntity<StorageUploadResult> response = storageController.uploadFile(file, "products");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(expectedResult, response.getBody());
        verify(storageFacade).uploadFile(file, "products");
    }

    @Test
    void deleteFile_CallsFacadeAndReturnsSuccessMessage() {
        doNothing().when(storageFacade).deleteFile("products/uuid.jpg");

        ResponseEntity<Map<String, String>> response = storageController.deleteFile("products/uuid.jpg");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Archivo eliminado correctamente", response.getBody().get("message"));
        verify(storageFacade).deleteFile("products/uuid.jpg");
    }
}
