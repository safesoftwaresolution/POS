<!-- spec-version: 1.0 | last-updated: 2026-10-02 -->
# Módulo: Storage (Almacenamiento de Archivos — Cloudflare R2)

## Bounded Context
Subida y eliminación de archivos binarios (documentos KYC, logos y otros archivos) en un bucket **Cloudflare R2** (compatible con S3). Otros módulos lo consumen vía `StorageFacade`.

## Dependencias
- **Requiere**: configuración `cloudflare.r2.*` (variables `R2_*`, ver `_deployment.md`).
- **Requerido por**: `stores` (documentos KYC) y `settings` (logo del negocio).

## Roles y Permisos
- Cualquier usuario autenticado (`/api/v1/**` exige autenticación); el controlador no aplica `@PreAuthorize` ni valida que la clave pertenezca al local (deuda registrada en `_roadmap.md`).

## Reglas de Negocio
- **RN-STOR-001 (Clave generada)**: la clave del objeto es `<carpeta>/<UUID><extensión>`; el nombre original nunca forma parte de la clave.
- **RN-STOR-002 (Carpeta saneada)**: el parámetro `folder` se sanea antes de usarse (`sanitizeFolder`). Carpeta por defecto: `products`.
- **RN-STOR-003 (Sin fallback)**: sin las variables `R2_*` el endpoint responde pero falla al conectar con R2; no hay almacenamiento local alterno.
- **RN-STOR-004 (Límite)**: tamaño máximo por archivo y por request: 10 MB (`spring.servlet.multipart.*`).

## API Pública (`StorageFacade`)
- `uploadFile(MultipartFile, folder)` y `uploadBytes(data, filename, contentType, folder)` → `StorageUploadResult` (`key`, `url` pública, `originalFilename`, `sizeBytes`, `contentType`).
- `deleteFile(key)`.

## Endpoints
- `POST /api/v1/storage/upload` — multipart: `file`, `folder` (opcional).
- `DELETE /api/v1/storage/files?key=...` — elimina un objeto por su clave.
