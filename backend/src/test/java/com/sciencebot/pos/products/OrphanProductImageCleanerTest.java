package com.sciencebot.pos.products;

import com.sciencebot.pos.products.internal.repositories.ProductRepository;
import com.sciencebot.pos.products.internal.services.OrphanProductImageCleaner;
import com.sciencebot.pos.storage.StorageFacade;
import com.sciencebot.pos.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrphanProductImageCleanerTest {

    private static final String BASE = "https://pub.r2.dev/";
    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");

    @Mock
    private StorageFacade storageFacade;

    @Mock
    private ProductRepository productRepository;

    private OrphanProductImageCleaner cleaner;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cleaner = new OrphanProductImageCleaner(storageFacade, productRepository, 24);
    }

    @Test
    void deletesOldUnreferencedPhotos_andKeepsReferencedAndRecentOnes() {
        when(productRepository.findAllImageUrls()).thenReturn(List.of(BASE + "imagenes-productos/tienda-1/usada.jpg"));
        when(storageFacade.listFiles("imagenes-productos/")).thenReturn(List.of(
                file("imagenes-productos/tienda-1/usada.jpg", Duration.ofDays(10)),
                file("imagenes-productos/tienda-1/huerfana.jpg", Duration.ofHours(25)),
                file("imagenes-productos/tienda-2/reciente.jpg", Duration.ofHours(2))));

        int deleted = cleaner.cleanOrphans(NOW);

        assertEquals(1, deleted);
        verify(storageFacade).deleteFile("imagenes-productos/tienda-1/huerfana.jpg");
        verify(storageFacade, never()).deleteFile("imagenes-productos/tienda-1/usada.jpg");
        verify(storageFacade, never()).deleteFile("imagenes-productos/tienda-2/reciente.jpg");
    }

    @Test
    void aFailureDeletingOnePhoto_DoesNotStopTheRest() {
        when(productRepository.findAllImageUrls()).thenReturn(List.of());
        when(storageFacade.listFiles("imagenes-productos/")).thenReturn(List.of(
                file("imagenes-productos/tienda-1/a.jpg", Duration.ofDays(2)),
                file("imagenes-productos/tienda-1/b.jpg", Duration.ofDays(2))));
        doThrow(new IllegalStateException("R2 caído")).when(storageFacade).deleteFile("imagenes-productos/tienda-1/a.jpg");

        int deleted = cleaner.cleanOrphans(NOW);

        assertEquals(1, deleted);
        verify(storageFacade).deleteFile("imagenes-productos/tienda-1/b.jpg");
    }

    @Test
    void nothingInTheBucket_DeletesNothing() {
        when(productRepository.findAllImageUrls()).thenReturn(List.of());
        when(storageFacade.listFiles(any())).thenReturn(List.of());

        assertEquals(0, cleaner.cleanOrphans(NOW));
        verify(storageFacade, never()).deleteFile(any());
    }

    @Test
    void scheduledRun_SwallowsFailuresListingR2() {
        when(productRepository.findAllImageUrls()).thenReturn(List.of());
        when(storageFacade.listFiles(any())).thenThrow(new IllegalStateException("sin red"));

        cleaner.scheduledRun();

        verify(storageFacade, never()).deleteFile(any());
    }

    private static StoredFile file(String key, Duration age) {
        return new StoredFile(key, BASE + key, NOW.minus(age));
    }
}
