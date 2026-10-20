<!-- spec-version: 1.2 | last-updated: 2026-09-16 -->
# Módulo: Categorías (Categories)

## Dependencias
- **Requiere**: ninguno
- **Requerido por**: products

## Resumen del módulo
Dos conceptos distintos conviven aquí:
- **Categorías** (`categories`): catálogo **global**, compartido por todas las tiendas, seedeado automáticamente (`CategorySeeder`) y editable solo por `SUPER_ADMIN`. Ver reglas RN-CAT-*.
- **Subcategorías** (`product_subcategories`): propias de **cada tienda**, creadas libremente por `ADMINISTRATOR`/`SUPERVISOR` de esa tienda bajo una categoría global existente — sin depender de la plataforma. Ver reglas RN-SUBCAT-*.

## Reglas de Negocio

### RN-CAT-001 — Unicidad de Nombre (Case Insensitive)
- Los nombres de categoría son únicos sin distinguir mayúsculas/minúsculas.

### RN-CAT-002 — Escritura Exclusiva de Plataforma
- Las categorías son un catálogo **global** (compartido por todas las tiendas, sin `store_id`, ver FEAT-006). Por eso crear, editar y eliminar está restringido exclusivamente al rol `SUPER_ADMIN`; ningún rol de tienda (Administrator/Supervisor/Seller) puede escribir el catálogo, solo leerlo.

## Requerimientos Funcionales

### RF-CAT-001 — Crear Categoría
- **Prioridad**: Alta
- **Criterios de Aceptación**:
  - Campos: name (único, case-insensitive), description (opcional).
  - Validar unicidad de nombre.

### RF-CAT-002 — Editar Categoría
- **Prioridad**: Alta
- Editable: name, description. Validar unicidad de nombre.

### RF-CAT-003 — Consultar Categorías
- **Prioridad**: Alta
- Listar todas las categorías con nombre y cantidad de productos asociados.

### RF-CAT-004 — Eliminar Categoría
- **Prioridad**: Media
- No se puede eliminar si tiene productos asociados activos.

## API Endpoints

### GET /api/v1/categories
- **Permisos**: SUPER_ADMIN, Admin, Supervisor, Vendedor (lectura abierta a cualquier rol autenticado)
- **Response 200**: Lista de categorías

### POST /api/v1/categories
- **Permisos**: SUPER_ADMIN exclusivamente
- **Request**: `{ "name": "Bebidas", "description": "Bebidas frías y calientes" }`
- **Response 201**: Categoría creada
- **Errores**: 400 (nombre duplicado), 403 (rol distinto de SUPER_ADMIN)

### PUT /api/v1/categories/{id}
- **Permisos**: SUPER_ADMIN exclusivamente

### DELETE /api/v1/categories/{id}
- **Permisos**: SUPER_ADMIN exclusivamente
- **Errores**: 409 (tiene productos asociados)

## Modelo de Datos

### Tabla: `categories`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| name | VARCHAR(50) | UNIQUE, NOT NULL |
| description | VARCHAR(255) | NULLABLE |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

## Permisos (Categorías)
| Operación | SUPER_ADMIN | Administrator | Supervisor | Seller |
|---|---|---|---|---|
| Crear | ✓ | - | - | - |
| Leer | ✓ | ✓ | ✓ | ✓ |
| Editar | ✓ | - | - | - |
| Eliminar | ✓ | - | - | - |

---

## Subcategorías (Subcategories)

### RN-SUBCAT-001 — Alcance por Tienda
- Cada subcategoría pertenece a una tienda (`store_id`) y a una categoría global (`category_id`). Una tienda solo ve y gestiona sus propias subcategorías; nunca las de otra tienda.

### RN-SUBCAT-002 — Unicidad por Tienda y Categoría
- El nombre de una subcategoría es único (case-insensitive) dentro de la combinación tienda + categoría padre. La misma tienda puede reutilizar el nombre en otra categoría; otra tienda puede reutilizarlo libremente.

### RN-SUBCAT-003 — Consistencia con la Categoría del Producto
- Al asignar `subcategoryId` a un producto, debe pertenecer a la misma `categoryId` que el producto y a la tienda actual. Es siempre opcional — un producto puede tener solo categoría, sin subcategoría.

### RN-SUBCAT-004 — Borrado Bloqueado por Uso
- No se puede eliminar una subcategoría con productos activos asociados (mismo patrón que RF-CAT-004). Tampoco se puede eliminar una categoría global que tenga subcategorías creadas por alguna tienda (evita huérfanas).

### RF-SUBCAT-001 — Crear Subcategoría
- **Prioridad**: Alta
- **Criterios de Aceptación**: Campos: categoryId (obligatorio, debe existir), name (obligatorio, único por tienda+categoría), description (opcional).

### RF-SUBCAT-002 — Editar Subcategoría
- **Prioridad**: Media
- Editable: name, description. La categoría padre (`categoryId`) no se puede cambiar tras crearla.

### RF-SUBCAT-003 — Consultar Subcategorías
- **Prioridad**: Alta
- Lista las subcategorías de la tienda actual; admite filtro opcional por `categoryId`.

### RF-SUBCAT-004 — Eliminar Subcategoría
- **Prioridad**: Media
- Ver RN-SUBCAT-004.

## API Endpoints (Subcategorías)

### GET /api/v1/subcategories
- **Permisos**: SUPER_ADMIN\*, Administrator, Supervisor, Seller (lectura, acotada a la tienda actual vía `TenantContext`)
- **Query params**: `categoryId` (opcional)

### POST /api/v1/subcategories
- **Permisos**: Administrator, Supervisor
- **Request**: `{ "categoryId": 2, "name": "Gaseosas", "description": "Bebidas carbonatadas" }`
- **Response 201**: Subcategoría creada
- **Errores**: 400 (categoría inexistente o nombre duplicado)

### PUT /api/v1/subcategories/{id}
- **Permisos**: Administrator, Supervisor

### DELETE /api/v1/subcategories/{id}
- **Permisos**: Administrator, Supervisor
- **Errores**: 409 (tiene productos asociados)

\* `SUPER_ADMIN` no tiene `store_id`/tienda activa, por lo que en la práctica esta ruta la usan los roles de tienda; se deja listada por consistencia con el resto de endpoints de solo lectura.

## Modelo de Datos (Subcategorías)

### Tabla: `product_subcategories`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| store_id | BIGINT | FK → stores(id), NOT NULL |
| category_id | BIGINT | FK → categories(id), NOT NULL |
| name | VARCHAR(50) | NOT NULL |
| description | VARCHAR(255) | NULLABLE |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

**Constraints**: `UK_subcategories_store_category_name` UNIQUE (store_id, category_id, name)

`products.subcategory_id` (nullable) referencia esta tabla — ver `modules/products/spec.md`.

## Permisos (Subcategorías)
| Operación | SUPER_ADMIN | Administrator | Supervisor | Seller |
|---|---|---|---|---|
| Crear | - | ✓ | ✓ | - |
| Leer | ✓ | ✓ | ✓ | ✓ |
| Editar | - | ✓ | ✓ | - |
| Eliminar | - | ✓ | ✓ | - |
