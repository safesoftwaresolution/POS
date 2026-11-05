package com.sciencebot.pos.settings.internal.services;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.settings.CompanyLogoResponseDto;
import com.sciencebot.pos.settings.CompanyLogoService;
import com.sciencebot.pos.settings.internal.entities.Setting;
import com.sciencebot.pos.settings.internal.repositories.SettingsRepository;
import com.sciencebot.pos.storage.StorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Service
public class CompanyLogoServiceImpl implements CompanyLogoService {

    private static final Logger log = LoggerFactory.getLogger(CompanyLogoServiceImpl.class);

    /** Raiz de los logos en R2; dentro va tienda-N/ igual que las fotos de productos. */
    private static final String LOGO_ROOT = "imagenes-logos/";

    private static final long MAX_FILE_SIZE_BYTES = 200 * 1024; // 200 KB = 204,800 bytes
    private static final int MAX_WIDTH_PX = 300;
    private static final int MAX_HEIGHT_PX = 300;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/pjpeg"
    );

    private final SettingsRepository settingsRepository;
    private final StorageFacade storageFacade;

    public CompanyLogoServiceImpl(SettingsRepository settingsRepository,
                                  @Autowired(required = false) StorageFacade storageFacade) {
        this.settingsRepository = settingsRepository;
        this.storageFacade = storageFacade;
    }

    @Override
    @Transactional
    public CompanyLogoResponseDto updateLogo(MultipartFile file) {
        // 1. Validar presencia del archivo
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar un archivo de imagen no vacío.");
        }

        // 2. Validar formato (solo PNG, JPG o JPEG)
        String originalFilename = file.getOriginalFilename();
        validateFileFormat(originalFilename, file.getContentType());

        // 3. Validar peso (< 200 KB)
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(String.format(
                    "El archivo pesa %.2f KB, superando el límite máximo permitido de 200 KB.",
                    file.getSize() / 1024.0
            ));
        }

        // 4. Validar dimensiones (<= 300x300 px) usando librerías estándar de Java
        BufferedImage image = validateImageDimensions(file);
        int width = image.getWidth();
        int height = image.getHeight();

        // 5. Guardado local en base de datos (entidad Setting)
        String localLogoUrl = saveLocally(file, originalFilename);

        log.info("Logo de la empresa actualizado. URL: {}", localLogoUrl);

        return new CompanyLogoResponseDto(
                localLogoUrl,
                originalFilename != null ? originalFilename : "logo",
                width,
                height,
                file.getSize(),
                "Logo de la empresa actualizado."
        );
    }

    /**
     * Valida la extensión y el Content-Type del archivo.
     */
    private void validateFileFormat(String filename, String contentType) {
        String ext = "";
        if (filename != null && filename.contains(".")) {
            ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Formato no permitido. Solo se aceptan imágenes PNG, JPG o JPEG.");
        }

        if (contentType != null && !contentType.isBlank()) {
            String mime = contentType.toLowerCase().trim();
            if (!ALLOWED_MIME_TYPES.contains(mime)) {
                throw new IllegalArgumentException("Formato no permitido. Solo se aceptan imágenes PNG, JPG o JPEG.");
            }
        }
    }

    /**
     * Lee la imagen con javax.imageio.ImageIO y valida que sus dimensiones sean <= 300x300 píxeles.
     */
    private BufferedImage validateImageDimensions(MultipartFile file) {
        BufferedImage image;
        try (InputStream is = file.getInputStream()) {
            image = ImageIO.read(is);
        } catch (IOException e) {
            throw new IllegalArgumentException("Error al leer el archivo de imagen: " + e.getMessage(), e);
        }

        if (image == null) {
            throw new IllegalArgumentException("El archivo suministrado no es una imagen válida o está corrupto.");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (width > MAX_WIDTH_PX || height > MAX_HEIGHT_PX) {
            throw new IllegalArgumentException(String.format(
                    "Las dimensiones de la imagen (%dx%d px) exceden el límite máximo de %dx%d píxeles.",
                    width, height, MAX_WIDTH_PX, MAX_HEIGHT_PX
            ));
        }

        return image;
    }

    /**
     * Guarda la imagen localmente (vía Cloudflare R2 si está configurado, o URL relativa)
     * y actualiza el campo logoUrl en la entidad Setting de la base de datos.
     */
    private String saveLocally(MultipartFile file, String originalFilename) {
        // Sin R2 no hay donde servir el logo: antes se guardaba una ruta /uploads/... que nadie sirve y la
        // imagen salia rota. Ahora el error llega al usuario y el logo anterior se conserva.
        if (storageFacade == null) {
            throw new IllegalStateException("El almacenamiento de archivos no esta configurado.");
        }

        Long storeId = TenantContext.getStoreId();
        String folder = LOGO_ROOT + (storeId != null ? "tienda-" + storeId : "plataforma");
        String resolvedLogoUrl = storageFacade.uploadFile(file, folder).url();

        Setting setting = resolveSetting(storeId);
        String previousUrl = setting.getLogoUrl();
        setting.setLogoUrl(resolvedLogoUrl);
        settingsRepository.save(setting);
        releaseReplacedLogo(previousUrl, resolvedLogoUrl);

        return resolvedLogoUrl;
    }

    /** Borra el logo anterior de R2 tras confirmar el guardado; un fallo solo se registra. */
    private void releaseReplacedLogo(String previousUrl, String currentUrl) {
        if (previousUrl == null || previousUrl.isBlank() || previousUrl.equals(currentUrl)) {
            return;
        }
        Runnable delete = () -> {
            try {
                // Estructura actual y la anterior (logos/), por los logos ya subidos.
                storageFacade.deleteFileByUrl(previousUrl, LOGO_ROOT, "logos/");
            } catch (RuntimeException e) {
                log.warn("No se pudo borrar el logo anterior {} de R2: {}", previousUrl, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
        } else {
            delete.run();
        }
    }

    /**
     * Obtiene o crea la entidad Setting correspondiente al storeId actual.
     */
    private Setting resolveSetting(Long storeId) {
        if (storeId != null) {
            return settingsRepository.findById(storeId).orElseGet(() -> {
                Setting s = new Setting();
                s.setId(storeId);
                s.setBusinessName("Mi Negocio");
                s.setAddress("Por definir");
                s.setPhone("Por definir");
                return settingsRepository.save(s);
            });
        }

        // Fallback cuando no hay storeId en contexto (ej. monotenant o test)
        return settingsRepository.findAll().stream().findFirst().orElseGet(() -> {
            Setting s = new Setting();
            s.setId(1L);
            s.setBusinessName("Mi Negocio");
            s.setAddress("Por definir");
            s.setPhone("Por definir");
            return settingsRepository.save(s);
        });
    }
}
