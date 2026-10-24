<!-- doc-version: 1.1 | last-updated: 2026-10-16 -->
# Convenciones de Código

## Estructura de Paquetes (Modular Monolith)

```
src/main/java/com/sciencebot/pos/
├── Application.java             ← Arranque Spring Boot (carga backend/.env)
│
├── config/                      ← Configuración técnica global
│   ├── security/                ← SecurityConfig (CORS, rutas), JwtAuthenticationFilter, JwtService
│   ├── tenant/                  ← TenantContext (storeId por solicitud)
│   ├── async/  http/  openapi/  ← @Async, OutboundHttp (RestClient con timeouts), Swagger
├── exceptions/                  ← GlobalExceptionHandler
│
├── shared/                      ← Utilidades transversales
│   ├── email/                   ← EmailSender (log | smtp)
│   └── validation/              ← Preconditions (requireNotNull, checkState, …)
│
└── [modulo]/                    ← Módulo de negocio (ej. sales, products)
    ├── [Module]Facade.java      ← Interfaz pública de entrada al módulo
    ├── [Module]Dto.java         ← DTOs de salida públicos
    ├── Create[Module]Command.java ← DTOs de entrada públicos (con anotaciones de Bean Validation)
    ├── [Module]CompletedEvent.java ← Eventos de dominio públicos (ej. SaleCompletedEvent)
    │
    └── internal/                ← Implementación privada (invisible a otros módulos)
        ├── controllers/         ← @RestController (y controllers/doc/ con la anotación OpenAPI)
        ├── services/            ← Lógica de negocio; implementan la Facade
        ├── repositories/        ← JpaRepository (o Dao con JdbcClient en reports)
        ├── entities/            ← Entidades JPA
        ├── mappers/             ← @Component Entity ↔ DTO
        └── adapters/ listeners/ config/  ← según el módulo (billing, storage, …)
```

> `billing` coloca su API pública en `billing/api`, `billing/dto` y `billing/exception` (ver `_architecture.md`).

---

## Reglas de Visibilidad e Inyección

1. **Encapsulación**: las clases de `internal/` deberían ser `package-private` cuando sea posible. **Hoy casi todas son `public` y no hay ArchUnit ni Modulith que lo verifique**; se cumple por revisión.
2. **Inyección entre módulos**: solo la interfaz `Facade` (o el servicio público de la raíz del módulo). Nunca clases de `internal/` de otro módulo.
3. **DTOs**: las entidades JPA (`@Entity`) nunca salen de su módulo; toda comunicación externa y entre módulos usa `record` inmutables.
4. **Validación de entrada**: los Commands/Requests públicos llevan anotaciones `jakarta.validation` y el controller usa `@Valid @RequestBody`. Requiere `spring-boot-starter-validation` (presente en el `pom.xml`); `MethodArgumentNotValidException` se traduce a 400 en `GlobalExceptionHandler`.

---

## Nombrado y Convenciones Java

| Elemento | Convención | Ejemplo |
|---|---|---|
| Clases e Interfaces | PascalCase | `ProductFacade`, `ProductServiceImpl` |
| DTOs | sufijo `Dto` (en billing hay `CreditNoteDTO`/`DebitNoteDTO`, a unificar) | `SaleDto` |
| Métodos y Variables | camelCase | `calculateTotalAmount()` |
| Constantes | UPPER_SNAKE_CASE | `MAX_LOGIN_ATTEMPTS` |
| Paquetes | minúsculas, singular | `com.sciencebot.pos.sales.internal` |

---

## Reglas por Capa Interna

### Controllers (`internal/controllers`)
- `@RestController` con rutas `/api/v1/[modulo]` (o `/api/v1/backoffice/...` para SUPER_ADMIN).
- Se comunican únicamente con el servicio interno de su módulo.
- Todo endpoint (salvo los públicos listados en `_security.md`) lleva `@PreAuthorize` con el rol permitido. Un endpoint sin anotación queda abierto a cualquier usuario autenticado.

### Services (`internal/services`)
- Implementan la `Facade` del módulo. `@Transactional(readOnly = true)` para lecturas; `@Transactional` para escrituras. Un método `readOnly` no debe escribir.
- Lo que sea de un local filtra por `TenantContext.getStoreId()`; si la operación lo exige, falla sin local en vez de asumir uno por defecto.
- Las llamadas HTTP salientes usan `OutboundHttp.restClient()`, nunca `RestClient.create()` (sin timeouts).

### Repositories (`internal/repositories`)
- Heredan de `JpaRepository<Entity, Long>`. Un recurso compartido que se modifica con ventas concurrentes (el stock) se cambia con un `UPDATE` atómico y condicionado, no leyendo y escribiendo la entidad; para documentos electrónicos se usa una consulta con `@Lock(PESSIMISTIC_WRITE)` cuando la fila no se cargó antes en la misma transacción (Hibernate no refresca una entidad ya cargada).

### Base de Datos y Migraciones
- `ddl-auto=validate` en dev y prod; solo el perfil `test` deja a Hibernate crear el esquema.
- Todo cambio de esquema es una migración Flyway en `src/main/resources/db/migration/` (ver `_database-schema.md`).
- Pon nombre explícito a toda restricción (`CONSTRAINT UK_tabla_col UNIQUE (...)`); un `UNIQUE` en línea recibe en PostgreSQL el nombre `<tabla>_<columna>_key`. Un `DROP CONSTRAINT IF EXISTS` con un nombre inexistente no falla ni hace nada (así V9 dejó la unicidad global vigente hasta V25); `MigrationConstraintNamesTest` lo vigila desde V25.

### Mapeo de Datos (Mappers)
- Prohibido mapear entidad ↔ DTO con métodos privados del servicio: usar una clase `@Component` en `internal/mappers/` inyectada por constructor.
- Incumplimientos conocidos: `BackofficeStaffServiceImpl.toStaffDto` y el armado de la solicitud en `CreditNoteServiceImpl.createFromSale`.

---

## Tests

- Directorio: `src/test/java/com/sciencebot/pos/`.
- Framework: JUnit 5 + Mockito + Spring Boot Test (`MockMvc`, `MockRestServiceServer` para los clientes Factus). **No hay ArchUnit ni Modulith ni Testcontainers**; todo corre sobre H2.
- Perfil `test`: `src/test/resources/application.properties` **reemplaza por completo** al de `main` (mismo nombre en el classpath). El `DatabaseSeeder` solo siembra datos de desarrollo en el perfil `dev`, así que un test que necesite un usuario debe crearlo.
- Migraciones: `V*MigrationSqlTest` ejecutan Flyway a mano contra su propia H2 en memoria.
- Los tests de seguridad por rol (`@PreAuthorize`) con `MockMvc` son escasos; los controllers se prueban casi siempre como POJOs. Al añadir un endpoint con permisos, cubrir al menos el 403.
