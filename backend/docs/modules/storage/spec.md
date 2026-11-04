<!-- spec-version: 1.0 | last-updated: 2026-10-16 -->
# Módulo: Storage (Almacenamiento en Cloudflare R2)

## Dependencias
- **Requiere**: ninguno (usa el SDK S3 de AWS contra el endpoint compatible de Cloudflare R2).
- **Requerido por**: `stores` (documentos KYC, carpeta `documentos`), `settings` (logo del negocio, carpeta `logos`) y el frontend (imágenes de productos y logos vía `POST /api/v1/storage/upload`).

## Bounded Context
Subida y borrado de archivos en un bucket R2. No guarda metadatos en base de datos: quien sube un archivo guarda la `url` y la `key` que devuelve.

## API pública del módulo (`StorageFacade`)
- `uploadFile(MultipartFile, folder)` → `StorageUploadResult(key, url, originalFilename, sizeBytes, contentType)`.
- `uploadBytes(byte[], filename, contentType, folder)` → igual que el anterior (para contenido generado, ej. PDF).
- `deleteFile(key)`.

## Reglas de Negocio
- **RN-STO-001 (Solo tipos permitidos)**: `uploadFile` acepta únicamente `.jpg/.jpeg`, `.png`, `.webp`, `.gif` y `.pdf`, y el `Content-Type` debe corresponder a la extensión. Se rechaza `.html`, `.svg`, `.exe`, etc. con 400: el bucket se sirve desde un dominio público, así que permitirlos habilitaría XSS almacenado.
- **RN-STO-002 (Carpeta saneada)**: `folder` solo admite segmentos `[A-Za-z0-9_-]` separados por `/`; `..` y caracteres especiales se rechazan.
- **RN-STO-003 (Aislamiento por local en el endpoint HTTP)**: `POST /api/v1/storage/upload` solo acepta las carpetas `imagenes-productos` e `imagenes-logos` y las organiza como `{carpeta}/tienda-{storeId}/` (ej. `imagenes-productos/tienda-4/uuid.jpg`), de modo que la raíz del bucket solo tiene las carpetas por tipo. `DELETE /api/v1/storage/files` solo borra claves que empiecen por `{carpeta}/tienda-{storeId}/` del local de la sesión (también acepta el prefijo antiguo `tienda-{storeId}/…` para limpiar archivos ya subidos) (y sin `..`); otra clave responde 403. Sin local en la sesión responde 403.
- **RN-STO-004 (Nombre no controlado por el cliente)**: la clave final es `{folder}/{UUID}{extensión}`; el nombre original solo se devuelve como metadato.
- **RN-STO-005 (Tamaño)**: el límite lo impone `spring.servlet.multipart` (10 MB por archivo/solicitud). Cada caso de uso puede ser más estricto (el logo, 200 KB y 300×300 px).
- **RN-STO-006 (Limpieza de fotos de producto)**: `products` borra de R2 la foto anterior al cambiarla o quitarla (tras el commit; un fallo de R2 solo se registra) y el formulario del frontend borra la foto subida que no llegó a guardarse. Como red de seguridad, `OrphanProductImageCleaner` corre a diario (`products.image-cleanup.cron`, por defecto 03:30) y borra de `imagenes-productos/` los archivos con más de `products.image-cleanup.retention-hours` (24) que ningún producto referencia; los productos desactivados cuentan como referencia. Solo recorre la estructura actual (`imagenes-productos/tienda-N/`), no el prefijo antiguo `tienda-N/imagenes-productos/`. Sin credenciales de R2 configuradas (dev) no hace nada. `StorageFacade.listFiles` recorre todas las páginas del bucket.

## Roles y Permisos
| Operación | SUPER_ADMIN | ADMINISTRATOR | SUPERVISOR | SELLER |
|---|---|---|---|---|
| Subir archivo (`POST /api/v1/storage/upload`) | - | ✓ | ✓ | - |
| Borrar archivo (`DELETE /api/v1/storage/files?key=`) | - | ✓ (solo claves del local) | ✓ (solo claves del local) | - |

## API Endpoints
- `POST /api/v1/storage/upload` — multipart: `file` y `folder` (`imagenes-productos` por defecto). Respuesta 200 con `StorageUploadResult`.
- `DELETE /api/v1/storage/files?key=` — 200 con `{message}`; 403 si la clave no pertenece al local.

## Configuración
`cloudflare.r2.account-id`, `access-key-id`, `secret-access-key`, `bucket-name`, `public-url` (variables `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET_NAME`, `R2_PUBLIC_URL`).

## Pendiente
- Sin credenciales R2 el cliente se construye con valores falsos y el fallo aparece al subir; debería fallar al arrancar en producción.
- Los documentos KYC (`documentos`) quedan en una URL pública; evaluar bucket privado con URLs prefirmadas.
- El cliente S3 no tiene timeouts propios configurados.
- Los servicios internos (`stores`, `settings`) llaman a `uploadFile` con carpetas fijas y **sin** prefijo de local; solo el endpoint HTTP aplica RN-STO-003.
