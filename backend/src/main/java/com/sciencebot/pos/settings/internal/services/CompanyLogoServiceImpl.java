package com.sciencebot.pos.settings.internal.services;

import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.exception.FactusApiException;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusTokenManager;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.settings.CompanyLogoResponseDto;
import com.sciencebot.pos.settings.CompanyLogoService;
import com.sciencebot.pos.settings.internal.entities.Setting;
import com.sciencebot.pos.settings.internal.repositories.SettingsRepository;
import com.sciencebot.pos.storage.StorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Service
public class CompanyLogoServiceImpl implements CompanyLogoService {

    private static final Logger log = LoggerFactory.getLogger(CompanyLogoServiceImpl.class);

    private static final String FACTUS_PROVIDER = "factus";

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
    private final FactusTokenManager factusTokenManager;
    private final StorageFacade storageFacade;
    private final RestClient restClient;

    private String factusBaseUrl;

    /**
     * Proveedor de facturación configurado. La sincronización del logo usa la API de Factus, así que
     * solo se intenta si el proveedor activo es Factus (otro proveedor o el mock no la exponen).
     */
    @Value("${billing.provider:factus}")
    private String billingProvider = FACTUS_PROVIDER;

    @Autowired
    public CompanyLogoServiceImpl(SettingsRepository settingsRepository,
                                  FactusTokenManager factusTokenManager,
                                  @Autowired(required = false) StorageFacade storageFacade) {
        this(settingsRepository, factusTokenManager, storageFacade,
                com.sciencebot.pos.config.http.OutboundHttp.restClient(), "https://api-sandbox.factus.com.co");
    }

    public CompanyLogoServiceImpl(SettingsRepository settingsRepository,
                                  FactusTokenManager factusTokenManager,
                                  StorageFacade storageFacade,
                                  RestClient restClient,
                                  String factusBaseUrl) {
        this.settingsRepository = settingsRepository;
        this.factusTokenManager = factusTokenManager;
        this.storageFacade = storageFacade;
        this.restClient = restClient != null ? restClient : com.sciencebot.pos.config.http.OutboundHttp.restClient();
        this.factusBaseUrl = factusBaseUrl != null ? factusBaseUrl : "https://api-sandbox.factus.com.co";
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

        // 6. Sincronización con Factus: solo si el local tiene la facturación electrónica activa.
        //    Sin ella el logo se guarda igual, solo localmente.
        boolean synced = syncLogoWithFactus(file, originalFilename);

        log.info("Logo de la empresa actualizado (sincronizado con Factus: {}). URL local: {}", synced, localLogoUrl);

        return new CompanyLogoResponseDto(
                localLogoUrl,
                originalFilename != null ? originalFilename : "logo",
                width,
                height,
                file.getSize(),
                synced,
                synced
                        ? "Logo actualizado en base de datos local y sincronizado exitosamente con Factus."
                        : isFactusActiveProvider()
                                ? "Logo actualizado en base de datos local. No se sincronizó con Factus porque la facturación electrónica del local no está activa."
                                : "Logo actualizado en base de datos local. No se sincronizó con Factus porque el proveedor de facturación configurado no es Factus."
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

    /**
     * Sincroniza la imagen con Factus API enviando un POST multipart/form-data a /v2/companies/logo.
     */
    private boolean syncLogoWithFactus(MultipartFile file, String originalFilename) {
        if (!isFactusActiveProvider()) {
            log.info("Logo no sincronizado: el proveedor de facturación configurado es '{}', no Factus", billingProvider);
            return false;
        }
        String token;
        String baseUrl;
        try {
            token = factusTokenManager != null ? factusTokenManager.getAccessToken() : null;
            String storeUrl = factusTokenManager != null ? factusTokenManager.getBaseUrl() : null;
            baseUrl = (storeUrl != null && !storeUrl.isBlank()) ? storeUrl : factusBaseUrl;
        } catch (BillingNotEnabledException ex) {
            log.info("Logo no sincronizado con Factus: {}", ex.getMessage());
            return false;
        } catch (FactusApiException ex) {
            throw new IllegalStateException("No se pudo autenticar con Factus para sincronizar el logo: " + ex.getMessage(), ex);
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("No se pudo obtener el token de autenticación para Factus API.");
        }

        String targetUrl = (baseUrl != null && !baseUrl.isBlank())
                ? baseUrl.replaceAll("/+$", "") + "/v2/companies/logo"
                : "https://api-sandbox.factus.com.co/v2/companies/logo";

        try {
            byte[] fileBytes = file.getBytes();
            String filename = (originalFilename != null && !originalFilename.isBlank()) ? originalFilename : "logo.png";

            ByteArrayResource resource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", resource);

            restClient.post()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Logo sincronizado exitosamente con Factus API en: {}", targetUrl);
            return true;

        } catch (RestClientResponseException ex) {
            log.error("Error devuelto por Factus API (HTTP {}): {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new IllegalStateException(
                    "Factus API rechazó el logo (HTTP " + ex.getStatusCode().value() + "): " + ex.getResponseBodyAsString(),
                    ex
            );
        } catch (IOException ex) {
            log.error("Error de E/S al preparar el archivo para Factus: {}", ex.getMessage());
            throw new IllegalStateException("Error al procesar los bytes de la imagen para Factus: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("Error inesperado al conectar con Factus API: {}", ex.getMessage());
            throw new IllegalStateException("Error de comunicación al sincronizar con Factus API: " + ex.getMessage(), ex);
        }
    }

    private boolean isFactusActiveProvider() {
        return billingProvider != null && FACTUS_PROVIDER.equals(billingProvider.trim().toLowerCase());
    }
}
