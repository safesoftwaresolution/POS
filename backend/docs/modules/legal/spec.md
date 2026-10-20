# Módulo: Legal (Textos Legales y CMS)

## Bounded Context
Gestión y publicación de documentos legales del SaaS (Políticas de Privacidad, Términos y Condiciones, Política de Cookies, etc.).

## Roles y Permisos
- **Público**: Lectura del documento vigente publicado vía `GET /api/v1/legal/{slug}`.
- `SUPER_ADMIN`: CRUD completo y publicación en `/api/v1/backoffice/legal-documents/**`.

## Reglas de Negocio
- **RN-LEG-001 (Unicidad de slug)**: Cada documento se identifica por un slug único (ej. `privacy-policy`, `terms-and-conditions`, `cookies-policy`).
- **RN-LEG-002 (Protección de eliminación)**: Un documento marcado como `published = true` no puede eliminarse; debe despublicarse primero.

## Endpoints
- `GET /api/v1/legal/{slug}`: Obtiene el texto legal publicado (público).
- `GET /api/v1/backoffice/legal-documents`: Listar todos los documentos legales.
- `POST /api/v1/backoffice/legal-documents`: Crear nuevo documento.
- `PUT /api/v1/backoffice/legal-documents/{id}`: Actualizar contenido y versión.
- `DELETE /api/v1/backoffice/legal-documents/{id}`: Eliminar borrador.
- `PATCH /api/v1/backoffice/legal-documents/{id}/publish`: Publicar/Despublicar.