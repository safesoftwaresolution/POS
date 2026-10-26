package com.sciencebot.pos.products.internal.services;

import com.sciencebot.pos.products.internal.repositories.ProductRepository;
import com.sciencebot.pos.storage.StorageFacade;
import com.sciencebot.pos.storage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Borra de R2 las fotos de producto que ningún producto referencia (subidas y nunca guardadas: pestaña
 * cerrada, sin señal, etc.). Solo toca archivos con más de {@code retention-hours} de antigüedad para no
 * pisar una foto que alguien acaba de subir y aún está guardando. Los productos desactivados siguen
 * contando como referencia: se pueden reactivar.
 */
@Component
public class OrphanProductImageCleaner {

    private static final Logger log = LoggerFactory.getLogger(OrphanProductImageCleaner.class);

    private static final String PRODUCT_IMAGES_PREFIX = "imagenes-productos/";

    private final StorageFacade storageFacade;
    private final ProductRepository productRepository;
    private final Duration retention;

    public OrphanProductImageCleaner(StorageFacade storageFacade,
                                     ProductRepository productRepository,
                                     @Value("${products.image-cleanup.retention-hours:24}") long retentionHours) {
        this.storageFacade = storageFacade;
        this.productRepository = productRepository;
        this.retention = Duration.ofHours(retentionHours);
    }

    @Scheduled(cron = "${products.image-cleanup.cron:0 30 3 * * *}")
    public void scheduledRun() {
        try {
            cleanOrphans(Instant.now());
        } catch (Exception e) {
            log.warn("Limpieza de fotos huérfanas omitida: {}", e.getMessage());
        }
    }

    /** @return cantidad de fotos huérfanas borradas */
    public int cleanOrphans(Instant now) {
        Instant cutoff = now.minus(retention);
        Set<String> referenced = new HashSet<>();
        productRepository.findAllImageUrls().forEach(url -> referenced.add(url.trim()));

        int deleted = 0;
        for (StoredFile file : storageFacade.listFiles(PRODUCT_IMAGES_PREFIX)) {
            boolean old = file.lastModified() != null && file.lastModified().isBefore(cutoff);
            if (!old || referenced.contains(file.url())) {
                continue;
            }
            try {
                storageFacade.deleteFile(file.key());
                deleted++;
            } catch (Exception e) {
                log.warn("No se pudo borrar la foto huérfana {}: {}", file.key(), e.getMessage());
            }
        }
        if (deleted > 0) {
            log.info("Limpieza de fotos huérfanas: {} archivo(s) borrado(s) de R2", deleted);
        }
        return deleted;
    }
}
