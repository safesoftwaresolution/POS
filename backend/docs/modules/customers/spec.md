<!-- spec-version: 1.2 | last-updated: 2026-10-29 -->
# Módulo: Clientes (Customers)

## Dependencias
- **Requiere**: users (identifica al usuario que crea o edita), audit (bitácora de cambios)
- **Requerido por**: sales

## Reglas de Negocio

### RN-CUST-001 — Cliente por Defecto
- Cada **local** posee un registro "Cliente General" (identification: "9999999999") que no puede editarse ni eliminarse (editarlo o borrarlo responde 400). Se crea al sembrar el local de desarrollo y, en producción, al vender sin cliente.
- **Datos para la DIAN** (`V2`): `customers` guarda además `dv`, `legal_organization_id` (13 = persona natural, 1 = jurídica), `tribute_id` (21 = consumidor final) y `municipality_id`; `store_id` desde `V3`. La unicidad de `identification` es por local.

### RN-CUST-002 — Cliente con Dueño
- Cada cliente guarda quién lo **creó** (`created_by`, `created_by_username`) y quién lo **editó por última vez** (`updated_by`, `updated_by_username`), desde `V30`. El creador original no cambia nunca; "editado por" solo cambia cuando hubo cambios reales.
- Un **vendedor** solo edita los clientes que él creó (`created_by` = su usuario); editar otro responde **403**. Los clientes anteriores a `V30` no tienen creador, así que el vendedor no los edita. El **administrador** y el **supervisor** editan cualquiera. Dar de baja sigue siendo solo del administrador.
- Todos los roles ven a todos los clientes del local. `CustomerDto.editable` le dice a la pantalla si el usuario actual puede editar ese cliente (el Cliente General nunca es editable).
- Lo decide el backend por el rol de la sesión; ocultar el botón en la pantalla es solo comodidad.

### RN-CUST-003 — Auditoría de Clientes
- Alta, edición y baja se registran en la bitácora del módulo `audit` (`entityType = CUSTOMER`) con el usuario y los campos que cambiaron (`fullName`, `identification`, `email`, `phone`, `address`, `dv`, `legalOrganizationId`, `tributeId`, `municipalityId`). El administrador consulta el historial en `GET /api/v1/audit`. Ver `audit/spec.md`.

## Requerimientos Funcionales

### RF-CUST-001 — Crear Cliente
- **Prioridad**: Alta
- Campos: fullName, identification (único), email, phone, address.
- Estado inicial: Activo.

### RF-CUST-002 — Editar Cliente
- **Prioridad**: Alta
- No editar "Cliente General" (RN-CUST-001).

### RF-CUST-003 — Consultar Clientes
- **Prioridad**: Alta
- Búsqueda por nombre o identification. Paginación.

### RF-CUST-004 — Eliminar Cliente (Lógico)
- **Prioridad**: Media
- Borrado lógico. No eliminar "Cliente General".

## API Endpoints

### GET /api/v1/customers
- **Permisos**: Admin, Supervisor, Vendedor
- **Query params**: search, page, size

### POST /api/v1/customers
- **Permisos**: Admin, Supervisor, Vendedor
- **Request**: `{ "fullName": "María López", "identification": "1234567890", "email": "maria@mail.com", "phone": "555-0100", "address": "Calle 1" }`
- **Response 201**: Cliente creado
- **Errores**: 400 (identification duplicado)

### PUT /api/v1/customers/{id}
- **Permisos**: Admin, Supervisor, Vendedor

### DELETE /api/v1/customers/{id}
- **Permisos**: Admin

## Modelo de Datos

### Tabla: `customers`
| Campo | Tipo | Restricciones |
|---|---|---|
| id | BIGINT | PK, Auto-increment |
| full_name | VARCHAR(100) | NOT NULL |
| identification | VARCHAR(50) | UNIQUE, NOT NULL |
| email | VARCHAR(100) | NULLABLE |
| phone | VARCHAR(20) | NULLABLE |
| address | VARCHAR(255) | NULLABLE |
| active | BOOLEAN | NOT NULL, DEFAULT TRUE |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |
| created_by / updated_by | BIGINT | NULLABLE (`V30`; sin FK) |
| created_by_username / updated_by_username | VARCHAR(50) | NULLABLE (`V30`; copia del nombre de usuario) |

## Permisos
| Operación | Admin | Supervisor | Vendedor |
|---|---|---|---|
| Crear | ✓ | ✓ | ✓ |
| Leer | ✓ | ✓ | ✓ |
| Editar | ✓ | ✓ | Solo los que creó (RN-CUST-002) |
| Eliminar | ✓ | - | - |
