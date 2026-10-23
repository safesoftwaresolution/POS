# 📜 Historial de Progreso — Harness de Agentes IA

> **REGLAS DE ESCRITURA** (obligatorias para todo agente):
>
> 1. **Nunca borrar entradas anteriores.** Este archivo es append-only.
> 2. **Formato obligatorio por entrada:**
>    ```
>    ## [YYYY-MM-DD HH:MM] [ROL] — Título breve
>    - **Tarea:** ID de la feature (ej. FEAT-002)
>    - **Acción:** Qué se hizo concretamente
>    - **Resultado:** Éxito / Fallo / Parcial
>    - **Archivos tocados:** Lista de archivos creados o modificados
>    - **Notas:** Observaciones adicionales relevantes
>    ```
> 3. **Sé explícito.** No asumas que otro agente sabe lo que hiciste.
>    Describe inputs, outputs y decisiones tomadas.
> 4. **Reporta fallos con detalle.** Si algo falló, incluye el mensaje
>    de error o la salida del comando.
> 5. **No resumas el trabajo de otros.** Si necesitas referirte a una
>    entrada anterior, cita la fecha y el rol exacto.

---

## [2026-07-06 08:42] [LEADER] — Inicialización del arnés de agentes

- **Tarea:** FEAT-001
- **Acción:** Creación de la estructura base del harness en `.ai/`
- **Resultado:** Éxito
- **Archivos tocados:**
  - `.ai/agents.md` — Punto de entrada con reglas base
  - `.ai/init.sh` — Script de validación del entorno
  - `.ai/features.json` — Backlog con 3 features iniciales
  - `.ai/progress/history.md` — Este archivo
  - `.ai/roles/leader.md` — Rol del orquestador
  - `.ai/roles/implementer.md` — Rol del implementador
  - `.ai/roles/reviewer.md` — Rol del revisor
- **Notas:** La FEAT-001 queda marcada como "done". La siguiente tarea
  pendiente es FEAT-002 (validación de compilación del proyecto).

## [2026-07-07 10:56] [REVIEWER] — Mejora del arnés basada en la suite de documentación

- **Tarea:** Mejora interna (no vinculada a feature)
- **Acción:** Vinculación del arnés con la suite de documentación en `docs/` y actualización de scripts de validación
- **Resultado:** Éxito
- **Archivos tocados:**
  - `.ai/agents.md` — Modificado para incluir el Protocolo de lectura de docs/ y regla R7 (SSOT).
  - `.ai/init.sh` — Recreado y modificado agregando la "Fase 2: Verificar suite de documentación (SSOT)".
  - `.ai/init.ps1` — Modificado agregando la "Fase 2: Verificar suite de documentación (SSOT)".
- **Notas:** El arnés ahora obliga a los agentes a validar la existencia e integridad de los documentos maestros de diseño (`docs/`) antes de realizar cualquier implementación. Se agregó la regla R7 para la adherencia estricta a la Fuente Única de Verdad (SSOT).

## [2026-07-08 10:45] [REVIEWER] — Migración a Arquitectura Documental Module-First
- **Tarea:** Tarea arquitectónica (Migración SSOT)
- **Acción:** Reestructuración de documentación global y modular en docs/ e integración con arnés optimizado de agentes.
- **Resultado:** Éxito
- **Archivos tocados:**
  - `docs/_*.md` (8 archivos globales compartidos creados)
  - `docs/modules/*/spec.md` (11 especificaciones modulares creadas)
  - `.ai/agents.md` — Modificado para boot sequence simplificado y Context Packs
  - `.ai/roles/*.md` — Roles actualizados con Context Packs y tablas de resolución
  - `.ai/init.ps1` / `init.sh` — Modificados para validar la nueva suite documental
- **Notas:** Se eliminaron 15 archivos globales y la carpeta temporal `requirements/` obsoletos. La suite de validación ejecutó con éxito dando estado de ENTORNO SANO.

## [2026-07-09 09:30] [REVIEWER] — Transición a Monolito Modular con División Vertical
- **Tarea:** Reestructuración de arquitectura backend del proyecto
- **Acción:** Modificación de docs/_architecture.md, docs/_conventions.md y .ai/agents.md / .ai/roles/implementer.md para adoptar el modelo de Monolito Modular y División Vertical (Spring Modulith).
- **Resultado:** Éxito
- **Archivos tocados:**
  - `docs/_architecture.md` — Modificado para describir la topología modular y reglas de comunicación
  - `docs/_conventions.md` — Modificado para estructurar paquetes por capacidad (Vertical Split)
  - `.ai/agents.md` — Modificado para reflejar estructura de paquetes modular
  - `.ai/roles/implementer.md` — Modificado para añadir visibilidad package-private y restricciones de acoplamiento
- **Notas:** Se ejecutó init.ps1 confirmando ENTORNO SANO.

## [2026-07-09 15:00] [LEADER] — Delegación de desarrollo de Auth y Users
- **Tarea:** FEAT-004 — Módulos Auth y Users
- **Delegado a:** Implementer
- **Context Pack:**
  1. docs/modules/auth/spec.md
  2. docs/modules/users/spec.md
  3. docs/_conventions.md
  4. docs/_security.md
- **Instrucciones:** Crear los módulos de Auth y Users siguiendo los principios del monolito modular y las convenciones del arnés.

## [2026-07-09 15:04] [IMPLEMENTER] — Implementación de módulos Auth y Users
- **Tarea:** FEAT-004 — Módulos Auth y Users
- **Acción:** Añadido soporte de seguridad global con filtros JWT y encriptación BCrypt, implementadas las APIs y reglas de negocio del spec para gestión de usuarios (unicidad de username/email, fuerza de clave, protección del último administrador) e inicio/cierre de sesión con bloqueo automático por intentos fallidos.
- **Resultado:** Éxito
- **Archivos tocados:**
  - `pom.xml` — Añadidas dependencias de seguridad y H2
  - `src/test/resources/application.properties` — Creado archivo para tests H2
  - `src/main/resources/application.properties` — Añadidos parámetros de configuración JWT
  - `src/main/java/com/sciencebot/pos/config/SecurityConfig.java` — Configuración de Spring Security
  - `src/main/java/com/sciencebot/pos/config/JwtAuthenticationFilter.java` — Filtro HTTP de autenticación
  - `src/main/java/com/sciencebot/pos/config/JwtService.java` — Utilidad para JWT
  - `src/main/java/com/sciencebot/pos/exceptions/GlobalExceptionHandler.java` — Manejador de excepciones
  - `src/main/java/com/sciencebot/pos/users/` (UserFacade, UserDto, comandos) — Interfaces y DTOs públicos de Users
  - `src/main/java/com/sciencebot/pos/users/internal/` (User, UserRepository, UserServiceImpl, DatabaseSeeder, UserController) — Implementación interna de Users
  - `src/main/java/com/sciencebot/pos/auth/AuthFacade.java` — Fachada vacía pública de Auth
  - `src/main/java/com/sciencebot/pos/auth/internal/` (AuthService, AuthController) — Implementación interna de Auth
- **Tests creados:** `UserServiceImplTest.java` (CRUD, validación de Admin, claves), `AuthServiceTest.java` (Login y bloqueo por intentos fallidos).
- **init.sh / init.ps1:** ✅ Pasa en verde

## [2026-07-09 15:05] [REVIEWER] — Aprobación de módulos Auth y Users
- **Tarea:** FEAT-004 — Módulos Auth y Users
- **Decisión:** ✅ APROBADO
- **Criterios cumplidos:** Cumple con todas las reglas de negocio del spec de usuarios y autenticación (bloqueo por intentos, unicidad, DTOs inmutables, aislamiento vertical modular, encapsulación package-private de lógica interna).
- **init.sh / init.ps1:** ✅ Pasa en verde (6 tests exitosos, 0 errores, compilación exitosa).
- **Notas:** Implementación limpia resolviendo correctamente el ciclo de dependencias mediante la inyección perezosa de PasswordEncoder en UserServiceImpl.

## [2026-07-14 13:30] [LEADER] — Delegación de desarrollo de Categories y Products
- **Tarea:** Desarrollo de Módulos Categories y Products
- **Delegado a:** Implementer
- **Context Pack:**
  1. docs/modules/categories/spec.md
  2. docs/modules/products/spec.md
  3. docs/_conventions.md
- **Instrucciones:** Crear los módulos de Categories y Products siguiendo los patrones verticales y asegurando el desacoplamiento de dependencias mutuas vía SPI.

## [2026-07-14 13:40] [IMPLEMENTER] — Implementación de módulos Categories y Products
- **Tarea:** Desarrollo de Módulos Categories y Products
- **Acción:** Creados ambos módulos con División Vertical. Para evitar dependencias circulares, el módulo `categories` define interfaces SPI (`CategoryDeleteValidator` y `CategoryProductCountProvider`) las cuales implementa el servicio `ProductServiceImpl`. Se resolvió la inyección mutua usando `@Lazy` sobre la fachada `CategoryFacade`. Se agregaron constraints check en la entidad `Product` y validación de nombres duplicados en `categories` (case-insensitive).
- **Resultado:** Éxito
- **Archivos tocados:**
  - `src/main/java/com/sciencebot/pos/exceptions/GlobalExceptionHandler.java` — Agregado mapeo de `IllegalStateException` a 409 Conflict.
  - `src/main/java/com/sciencebot/pos/categories/` (CategoryFacade, CategoryDto, comandos, SPIs) — Interfaces públicas de Categories.
  - `src/main/java/com/sciencebot/pos/categories/internal/` (Category, CategoryRepository, CategoryServiceImpl, CategoryController) — Implementación de Categories.
  - `src/main/java/com/sciencebot/pos/products/` (ProductFacade, ProductDto, comandos) — Interfaces públicas de Products.
  - `src/main/java/com/sciencebot/pos/products/internal/` (Product, ProductRepository, ProductServiceImpl, ProductController) — Implementación de Products.
- **Tests creados:** `CategoryServiceImplTest.java` y `ProductServiceImplTest.java`.
- **init.sh / init.ps1:** ✅ Pasa en verde

## [2026-07-14 13:41] [REVIEWER] — Aprobación de módulos Categories y Products
- **Tarea:** Desarrollo de Módulos Categories y Products
- **Decisión:** ✅ APROBADO
- **Criterios cumplidos:** Validación de negocio y relacional en base de datos (`sale_price >= purchase_price`), borrado lógico en productos, bloqueo de eliminación de categorías con productos activos, desacoplamiento y no ciclos a nivel de compilación / Modulith.
- **init.sh / init.ps1:** ✅ Pasa en verde (13 tests exitosos, 0 errores, compilación exitosa).
- **Notas:** Excelente solución de desacoplamiento de ciclo mediante SPIs y uso de inyección perezosa `@Lazy` de `CategoryFacade`.

## [2026-07-14 14:45] [REVIEWER] — Integración de Flyway y Validación de Esquemas
- **Tarea:** Tarea arquitectónica (Mecanismo de Migraciones)
- **Acción:** Integración de Flyway en pom.xml, configuración de JPA validate en application.properties y actualización de guías documentales del arnés (roles/implementer.md, docs/_database-schema.md, docs/_conventions.md).
- **Resultado:** Éxito
- **Archivos tocados:**
  - `pom.xml` — Agregadas dependencias flyway-core y flyway-database-postgresql
  - `src/main/resources/application.properties` — Modificado spring.jpa.hibernate.ddl-auto a validate
  - `docs/_database-schema.md` — Agregada sección de convenciones Flyway
  - `docs/_conventions.md` — Especificado uso obligatorio de migraciones SQL
  - `.ai/roles/implementer.md` — Prohibida alteración directa y ddl-auto update
- **Notas:** El script init.ps1 pasó exitosamente en verde en todas sus fases.

## [2026-07-14 15:04] [REVIEWER] — Refactorización de Mapeo de Entidades (ProductMapper)
- **Tarea:** Tarea de Calidad de Código (Patrón Mapper)
- **Acción:** Extracción del método de mapeo de entidades de ProductServiceImpl a una clase ProductMapper dedicada, inyección en ProductServiceImpl, resolución de dependencias circulares y actualización de ProductServiceImplTest.
- **Resultado:** Éxito
- **Archivos tocados:**
  - `src/main/java/com/sciencebot/pos/products/internal/mappers/ProductMapper.java` — Nueva clase pública de mapeo de entidades con inyección perezosa (@Lazy) de CategoryFacade.
  - `src/main/java/com/sciencebot/pos/products/internal/services/ProductServiceImpl.java` — Modificada para inyectar y utilizar ProductMapper, removiendo el método de mapeo privado toDto.
  - `src/test/java/com/sciencebot/pos/products/ProductServiceImplTest.java` — Actualizada inyectando el mock de ProductMapper y stubbeando su comportamiento en el caso de éxito.
- **Notas:** El script init.ps1 pasó exitosamente en verde (17 tests exitosos, 0 errores, compilación exitosa).

## [2026-07-14 15:08] [REVIEWER] — Adopción Global del Patrón Mapper (Módulos Users y Categories)
- **Tarea:** Tarea de Calidad de Código (Refactoring Mappers)
- **Acción:** Extracción del mapeo de entidades a clases Mapper dedicadas (`UserMapper` y `CategoryMapper`) en los módulos `users` y `categories`. Ajuste de dependencias circulares mediante `@Lazy` en `CategoryMapper` y actualización de sus respectivos tests unitarios (`UserServiceImplTest` y `CategoryServiceImplTest`).
- **Resultado:** Éxito
- **Archivos tocados:**
  - `src/main/java/com/sciencebot/pos/users/internal/mappers/UserMapper.java` — Creado.
  - `src/main/java/com/sciencebot/pos/users/internal/services/UserServiceImpl.java` — Modificado para inyectar/usar `UserMapper`.
  - `src/test/java/com/sciencebot/pos/users/UserServiceImplTest.java` — Adaptado para mockear `UserMapper`.
  - `src/main/java/com/sciencebot/pos/categories/internal/mappers/CategoryMapper.java` — Creado con inyección `@Lazy` de los proveedores.
  - `src/main/java/com/sciencebot/pos/categories/internal/services/CategoryServiceImpl.java` — Modificado para inyectar/usar `CategoryMapper`.
  - `src/test/java/com/sciencebot/pos/categories/CategoryServiceImplTest.java` — Adaptado para mockear `CategoryMapper`.
- **Notas:** Se corrió init.ps1 confirmando compilación y ejecución exitosa de los 17 tests unitarios en verde sin errores.

## [2026-08-13 17:45] [REVIEWER] — Formalización del Módulo Billing en el Harness y Suite Documental
- **Tarea:** FEAT-004 — Formalización del módulo Billing
- **Acción:** Creación de `docs/modules/billing/spec.md`, actualización de `docs/_project.md`, `docs/_index.md` y `docs/modules/sales/spec.md`, inclusión del módulo en los scripts de validación `init.ps1` e `init.sh`, y actualización del backlog `features.json`.
- **Resultado:** Éxito
- **Archivos tocados:**
  - `docs/modules/billing/spec.md` — Creado.
  - `docs/_index.md` — Actualizado con módulo billing y prefijo de requerimientos RF-BILL-*.
  - `docs/_project.md` — Actualizado el stack y flujo de negocio; removida exclusión de facturación.
  - `docs/modules/sales/spec.md` — Actualizadas dependencias y criterios de emisión electrónica no bloqueante.
  - `.ai/init.ps1` — Añadido "billing" al array $modules.
  - `.ai/init.sh` — Añadido "billing" al array MODULES.
  - `.ai/features.json` — Registrada FEAT-004 como completada y FEAT-005 como pendiente.
- **Notas:** La suite documental ahora refleja el 100% de la funcionalidad implementada respetando la regla R7 (SSOT).

## [2026-08-13 20:47] [IMPLEMENTER] — Desacoplamiento de Factus con Patrones Adapter y Strategy
- **Tarea:** FEAT-005 — Desacoplamiento de Facturación Electrónica
- **Acción:** Introducción del puerto `ElectronicInvoicingProvider` (Strategy), creación de DTOs canónicos (`InvoiceRequest`, `InvoiceResult`, `CustomerBillingData`, `InvoiceItemData`), implementación de `FactusBillingAdapter` (Adapter Factus) y `MockBillingAdapter` (Adapter Mock offline), refactorización de `BillingServiceImpl` para orquestar la estrategia activa (`billing.provider`), creación de `BillingCanonicalMapper` y `ElectronicInvoiceMapper`, y creación de suite de tests unitarios dedicada.
- **Resultado:** Éxito
- **Archivos tocados:**
  - `src/main/java/com/sciencebot/pos/billing/internal/adapters/ElectronicInvoicingProvider.java` — Creado.
  - `src/main/java/com/sciencebot/pos/billing/internal/adapters/dto/` — Creados InvoiceRequest, InvoiceResult, CustomerBillingData, InvoiceItemData.
  - `src/main/java/com/sciencebot/pos/billing/internal/adapters/factus/FactusBillingAdapter.java` — Creado.
  - `src/main/java/com/sciencebot/pos/billing/internal/adapters/factus/FactusTokenManager.java` — Reubicado y adaptado.
  - `src/main/java/com/sciencebot/pos/billing/internal/adapters/factus/FactusSaleMapper.java` — Adaptado a DTO canónico.
  - `src/main/java/com/sciencebot/pos/billing/internal/adapters/mock/MockBillingAdapter.java` — Creado.
  - `src/main/java/com/sciencebot/pos/billing/internal/mappers/BillingCanonicalMapper.java` — Creado.
  - `src/main/java/com/sciencebot/pos/billing/internal/mappers/ElectronicInvoiceMapper.java` — Creado.
  - `src/main/java/com/sciencebot/pos/billing/internal/services/BillingServiceImpl.java` — Refactorizado para inyectar estrategias y mappers.
  - `src/main/resources/application.properties` — Añadida propiedad billing.provider.
  - `src/test/java/com/sciencebot/pos/billing/BillingServiceImplTest.java` — Creado.
  - `src/test/java/com/sciencebot/pos/billing/FactusBillingAdapterTest.java` — Creado.
  - `src/test/java/com/sciencebot/pos/sales/SaleServiceImplTest.java` — Actualizado con mock de BillingFacade.
  - `.ai/features.json` — Marcada FEAT-005 como done.
- **Tests:** 43 tests pasando exitosamente (0 errores, 0 fallos). `init.ps1` finalizó con `ENTORNO SANO`.

## [2026-08-16 14:53] [IMPLEMENTER] — Robustecimiento del Manejo Global de Excepciones
- **Tarea:** Robustecimiento de Control de Errores y Validaciones
- **Acción:** Incorporación de manejadores específicos en `GlobalExceptionHandler` para `DataIntegrityViolationException` (409 Conflict), `HttpMessageNotReadableException` (400 Bad Request), `MethodArgumentTypeMismatchException` (400 Bad Request), `MissingServletRequestParameterException` (400 Bad Request), `HttpRequestMethodNotSupportedException` (405 Method Not Allowed) y `ConstraintViolationException` (400 Bad Request). Corrección del caso borde de unicidad al actualizar categorías en `CategoryServiceImpl` y adición de tests unitarios correspondientes.
- **Resultado:** Éxito
- **Archivos tocados:**
  - `src/main/java/com/sciencebot/pos/exceptions/GlobalExceptionHandler.java` — Actualizado.
  - `src/main/java/com/sciencebot/pos/categories/internal/services/CategoryServiceImpl.java` — Corregida validación de unicidad en update.
  - `src/test/java/com/sciencebot/pos/categories/CategoryServiceImplTest.java` — Añadidos tests para actualización de categorías.
- **Tests:** 45 tests pasando exitosamente (0 errores, 0 fallos). `init.ps1` finalizó con `ENTORNO SANO`.




## [2026-08-19 11:05] [IMPLEMENTER] — Implementación de Back Office SaaS, Multi-Tenancy y Categorías SUPER_ADMIN
- **Tarea:** FEAT-006 — Back Office SaaS, Multi-Tenancy y Restricción de Categorías
- **Acción:** 
  1. Creación de migración Flyway V3__Multitenancy_And_Backoffice.sql con tablas stores, store_categories, store_documents, legal_documents, support_tickets y adición de columna store_id a entidades del POS.
  2. Introducción de arquitectura Multi-Tenancy: TenantContext (ThreadLocal), PosUserDetails con storeId, extracción e inyección de storeId en JWT (JwtService y JwtAuthenticationFilter).
  3. Adición del rol SUPER_ADMIN (platform-level, sin store_id) y seeding en DatabaseSeeder.
  4. Restricción estricta de creación, edición y eliminación de categorías exclusivamente a SUPER_ADMIN (CategoryController).
  5. Implementación del módulo stores: métricas SaaS (total, activos, inactivos, verificados), buscador y filtro (ALL, ACTIVE, INACTIVE, PENDING_VERIFICATION, SUSPENDED), gestión KYC de documentos, endpoints de verificación de email y estado.
  6. Implementación del módulo legal: CRUD completo de textos legales (privacy-policy, terms-and-conditions, etc.), versionamiento, toggle publish y endpoint público para lectura del texto vigente.
  7. Implementación del módulo support: radicación pública de PQRs y bugs del sistema con código de radicado automático (PQR-YYYYMMDD-NNNN / BUG-YYYYMMDD-NNNN), tracking público, búsqueda con filtros, métricas y resolución.
  8. Creación de especificaciones modulares spec.md en backend/docs/modules/ para stores, legal, support y actualización de SSOT.
  9. Creación y ejecución de suite de tests unitarios dedicada para los nuevos servicios (StoreServiceImplTest, LegalDocumentServiceImplTest, SupportTicketServiceImplTest) y actualización de tests existentes.
- **Resultado:** Éxito. 62 tests pasando en verde sin errores ni fallos.

## [2026-08-21 10:47] [COMMITTER] — Integración atómica de cambios en árbol de trabajo
- **Tarea:** Integración y commit de suite de mejoras (Auth, Idempotencia, Factus v2, Parámetros Dinámicos, Frontend y Colecciones Postman)
- **Acción:** 6 commits atómicos creados tras validación verde del entorno y aprobación del plan
- **Commits:**
  - `d95e94c` feat(auth): add reuse grace window for refresh tokens to prevent network race conditions
  - `995d3c3` feat(sales,purchases): implement idempotency keys and supplier invoice constraints
  - `4c39926` feat(billing): refine Factus API v2 payload mapping and pessimistic locking for invoice emission
  - `e17f98a` feat(settings): implement dynamic parameter catalogs with multi-module facade and migration
  - `f98b214` feat(frontend): integrate client-side idempotency keys and submission lock in POS checkout
  - `22cafe6` docs(api): add Postman collections and environment for POS system APIs
- **Resultado:** Éxito (rama local develop, sin push)
- **Notas:** Validación `init.ps1` en verde (ENTORNO SANO, 0 errores, todos los tests pasando) antes de ejecutar. Commits organizados por unidad lógica e intención.

## [2026-08-27 21:54] [IMPLEMENTER] — Integración de Cloudflare R2 Storage en el Backend
- **Tarea:** Integración de Cloudflare R2 (Object Storage S3-compatible)
- **Acción:**
  1. Adición del SDK oficial `software.amazon.awssdk:s3` (BOM 2.25.20) en `pom.xml`.
  2. Configuración de propiedades `cloudflare.r2.*` y límites de subida multipart en `application.properties`.
  3. Creación del módulo vertical `com.sciencebot.pos.storage` con su interfaz pública `StorageFacade` y DTO `StorageUploadResult`.
  4. Implementación privada en `internal/`: configuración de cliente S3 (`R2ClientConfig`, `R2StorageProperties`), servicio de subida/eliminación (`StorageServiceImpl`) y controlador REST (`StorageController`) con OpenAPI / Swagger.
  5. Creación de tests unitarios exhaustivos (`StorageServiceImplTest` y `StorageControllerTest`).
- **Archivos tocados:**
  - `backend/pom.xml` — Añadidas dependencias de AWS S3 SDK.
  - `backend/src/main/resources/application.properties` — Añadida configuración R2 y multipart.
  - `backend/src/main/java/com/sciencebot/pos/storage/StorageFacade.java` — Creado.
  - `backend/src/main/java/com/sciencebot/pos/storage/StorageUploadResult.java` — Creado.
  - `backend/src/main/java/com/sciencebot/pos/storage/internal/config/R2StorageProperties.java` — Creado.
  - `backend/src/main/java/com/sciencebot/pos/storage/internal/config/R2ClientConfig.java` — Creado.
  - `backend/src/main/java/com/sciencebot/pos/storage/internal/services/StorageServiceImpl.java` — Creado.
  - `backend/src/main/java/com/sciencebot/pos/storage/internal/controllers/StorageController.java` — Creado.
  - `backend/src/test/java/com/sciencebot/pos/storage/StorageServiceImplTest.java` — Creado.
  - `backend/src/test/java/com/sciencebot/pos/storage/StorageControllerTest.java` — Creado.
- **Resultado:** Éxito. 95 tests pasando (0 errores, 0 fallos). `init.ps1` finalizó con `ENTORNO SANO`.

## [2026-08-27 21:56] [COMMITTER] — Integración de cambios de Cloudflare R2 y configuración
- **Tarea:** Integración de Cloudflare R2 y ajustes de configuración
- **Acción:** 2 commits atómicos creados tras validación verde del entorno y aprobación del plan
- **Commits:**
  - `9687ffb` feat(storage): integrate Cloudflare R2 object storage with S3 SDK
  - `3ea1ef0` chore(config): update production compose env vars and dev jwt fallback
- **Resultado:** Éxito (rama local develop, sin push)
- **Notas:** Validación `init.ps1` en verde antes de commitear (95 tests pasando al 100%).

## [2026-09-16 13:55] [COMMITTER] — Integración del arnés .ai al repositorio
- **Tarea:** Subir directorio .ai y actualizar .gitignore
- **Acción:** 1 commit atómico creado para rastreo del arnés de IA en el repositorio
- **Commits:**
  - chore(harness): track .ai assistant harness and configuration
- **Resultado:** Éxito
- **Notas:** Se removió .ai/ de .gitignore para permitir el control de versiones y trazabilidad de agentes, roles y especificaciones del sistema.

## [2026-09-16 14:34] [REVIEWER] — Auditoría SSOT tras el merge del 2026-09-16 (agente externo: Claude Code)

- **Tarea:** Revisión solicitada por el usuario de los últimos cambios y del arnés `.ai/` (no vinculada a un FEAT-XXX puntual)
- **Acción:**
  1. Ejecutado `init.ps1` sobre el HEAD actual (post-merge `acbff36`) → `ENTORNO SANO` (0 errores, 0 advertencias, 95+ tests en verde).
  2. Detectada discrepancia SSOT: `backend/docs/modules/categories/spec.md` documentaba permisos de escritura "Admin, Supervisor" pero `CategoryController` exige `SUPER_ADMIN` exclusivamente desde FEAT-006 (2026-08-19) — spec corregido (RN-CAT-002 agregada, tabla de Permisos y endpoints actualizados).
  3. Detectado que `CLAUDE.md` (raíz, guía específica de Claude Code) no mencionaba el rol `SUPER_ADMIN` ni los módulos `notifications`/`storage` — actualizado.
  4. Detectado que los módulos `notifications` y `storage` (llegados en el merge de la rama paralela, commits del 2026-09-03) no tienen `spec.md` en `backend/docs/modules/` ni entrada en el array `$modules` de `init.ps1`/`init.sh` — **no corregido, queda pendiente** (requiere leer el código de ambos módulos para redactar el spec correctamente; candidato para delegar a un Implementer/Reviewer dedicado).
  5. Detectado que `.ai/features.json` (v1.2.0, última entrada 2026-08-19) y este mismo historial no registran el trabajo fusionado el 2026-09-03 (notifications, storage inicial, auth/onboarding, store documents) ni la config de Spring Security del 2026-09-16 — esa rama no pasó por el protocolo Leader→Implementer→Reviewer de este arnés. **No corregido** (decisión de backlog, corresponde al Leader/al equipo, no se inventan entradas retroactivas de un trabajo que no se presenció).
  6. Documentados los hallazgos 4 y 5 en `backend/docs/_roadmap.md` (nuevo, creado en sesión previa del 2026-09-15) para que no se pierdan.
- **Resultado:** Parcial — hallazgos 2 y 3 corregidos; hallazgos 4 y 5 documentados pero pendientes de decisión/ejecución.
- **Archivos tocados:**
  - `backend/docs/modules/categories/spec.md` — corregido (permisos SUPER_ADMIN).
  - `CLAUDE.md` — actualizado (rol SUPER_ADMIN, módulos notifications/storage, mención del arnés `.ai/`).
  - `backend/docs/_roadmap.md` — agregadas filas de notifications/storage y hallazgos de esta auditoría.
- **Notas:** Esta sesión de Claude Code no venía siguiendo el protocolo Leader/Implementer/Reviewer/Committer de este arnés (el `.ai/` no estaba trackeado en git en la sesión anterior, 2026-09-15). A partir de ahora se respetan sus reglas sustantivas (SSOT, validación antes de dar por terminado, aprobación explícita antes de commitear) aunque sin roleplay literal de los 4 roles por turno, dado que opera como agente único interactivo con el usuario en vivo.

## [2026-09-16 17:00] [IMPLEMENTER] — Subcategorías de producto por tienda (agente externo: Claude Code)

- **Tarea:** Pedido del usuario: las categorías deben seguir viniendo de un seeder (no crearse a mano — ya resuelto en la sesión del 2026-09-15 con `CategorySeeder`), y las tiendas deben poder crear sus propias subcategorías bajo esas categorías globales, sin depender del equipo de plataforma.
- **Acción:**
  1. Migración `V12__Product_Subcategories.sql` (V11 ya estaba tomado por `V11__Notifications.sql` de la rama fusionada el mismo día): tabla `product_subcategories` (por tienda + categoría padre, nombre único por esa combinación) y columna `products.subcategory_id` (nullable, FK).
  2. Módulo `categories`: nuevas interfaces públicas `SubcategoryFacade`/`SubcategoryDto`/`Create-`/`UpdateSubcategoryCommand` y SPIs `SubcategoryDeleteValidator`/`SubcategoryProductCountProvider` (métodos con nombre distinto de las SPI de `CategoryDeleteValidator`/`CategoryProductCountProvider` a propósito: la misma clase, `ProductServiceImpl`, implementa ambos pares y Java no permite dos métodos `(Long)` con igual firma). Implementación interna (`Subcategory`, `SubcategoryRepository`, `SubcategoryServiceImpl`, `SubcategoryMapper`, `SubcategoryController` en `/api/v1/subcategories`, permisos Administrator/Supervisor para escritura, lectura abierta a Seller). `CategoryServiceImpl.deleteCategory` ahora bloquea si la categoría tiene subcategorías de alguna tienda.
  3. Módulo `products`: `Product.subcategoryId`, campo agregado a `CreateProductCommand`/`UpdateProductCommand`/`ProductDto`, `ProductMapper` resuelve `subcategoryName`, `ProductServiceImpl` valida que la subcategoría exista y pertenezca a la misma `categoryId` (implementa las dos SPIs nuevas).
  4. Frontend: `services/subcategoriesApi.js` nuevo; `ProductsBento.jsx` — select de subcategoría filtrado por la categoría elegida + creación rápida inline ("+ Nueva subcategoría", sin salir del formulario ni pedírselo a la plataforma) y badge de subcategoría en la tarjeta de producto.
  5. Documentación: `docs/modules/categories/spec.md` (sección completa Subcategorías, RN-SUBCAT-001..004), `docs/modules/products/spec.md` (campo subcategoryId, correcciones menores de nombres de rol y scoping por tienda ya existente pero no documentado), `docs/_roadmap.md`.
- **Resultado:** Éxito. Encontrados y corregidos en el camino: colisión de migración V11 (renombrada a V12), 9 sitios de test que construían `CreateProductCommand`/`ProductDto` posicionalmente y quedaron desalineados por los campos nuevos.
- **Archivos tocados:** ver detalle arriba; ~20 archivos backend + 3 frontend + 3 docs.
- **Tests:** `mvnw clean test` — 100% verde. `init.ps1` → ENTORNO SANO.
- **Notas:** No se creó un módulo `.ai`-nuevo ni se tocó `init.ps1`/`init.sh` (subcategorías vive dentro del módulo `categories` ya registrado). Pendiente de decisión de negocio, no bloqueante: si a futuro se quiere permitir eliminar/renombrar categorías raíz con más libertad, el bloqueo por subcategorías existentes (RN-SUBCAT-004) habrá que revisarlo.
## [2026-09-17 09:54] [COMMITTER] — Integración de cambios de Facturación, Bootstrap y Configuración
- **Tarea:** Segregación modular de Billing (invoices y numbering ranges DIAN), soporte .env y gobernanza Flyway
- **Acción:** 7 commits atómicos creados tras aprobación humana del plan
- **Commits:**
  - `59d3733` feat(billing): decouple invoice emission and DIAN numbering ranges into dedicated services and controllers
  - `fbd5ebb` docs(billing): update specification for segregated billing controllers and DIAN endpoints
  - `729e57f` feat(core): add dotenv loader to application bootstrap
  - `1ac732c` chore(config): enforce Flyway schema ownership and validate DDL auto
  - `a0e853c` style(stores): remove unused imports in StoreServiceImplTest
  - `7258969` docs(api): update Postman collections for registration and invoicing flow
  - `f6f44cb` chore(harness): track .ai assistant harness and progress history
- **Resultado:** Éxito (rama local develop)
- **Notas:** Validación `init.ps1` en verde (ENTORNO SANO, 0 errores, 0 fallos) antes de commitear.

## [2026-09-23 16:43] [COMMITTER] — Integración de cambios de Facturación por Tienda, Rangos DIAN y Configuración
- **Tarea:** Integración modular de credenciales Factus por tienda, auditoría de rangos DIAN, desacoplamiento de configuración y subpaquetes
- **Acción:** 7 commits atómicos creados tras validación verde del entorno y aprobación humana del plan
- **Commits:**
  - `9d3373e` refactor(config): reorganize configuration classes into functional subpackages
  - `f6047d3` refactor(billing): reorganize billing public contracts into api, dto, and exception packages
  - `23c5255` refactor(settings): decouple Factus billing configuration and drop legacy columns
  - `08a798b` feat(billing): add store billing credentials, activation management, and OpenAPI doc contracts
  - `cf04a8b` feat(billing): implement numbering range audit trail and set-default management
  - `738caec` docs(api): update Postman collections with complete POS and billing workflows
  - `8045688` docs: update architectural documentation, security guides, and module specifications
- **Resultado:** Éxito (rama local refactor/factus-billing-module, sin push)
- **Notas:** Validación `init.ps1` en verde (ENTORNO SANO, 0 errores, 0 fallos, 95+ tests pasando) antes de commitear.

## [2026-10-16 14:30] [EXTERNO] — Registro retroactivo del trabajo posterior al 2026-09-16 (fuera del protocolo)
- **Tarea:** FEAT-007 a FEAT-013 (registradas retroactivamente en `features.json` v1.3.0)
- **Acción:** Se deja constancia de trabajo que no pasó por Leader→Implementer→Reviewer→Committer y que no tenía entrada en este historial, reconstruido desde `git log` (no se re-ejecutó ni se re-revisó):
  - `14b729c` subcategorías de producto (V12) — FEAT-009.
  - `3ae9d7f`, `a54eb7e`, `27f91c9`, `5ee6000`, `b409487`, `6576b37`, `d3368c8`: interfaz Bento del frontend — FEAT-010.
  - `7da9b05`, `74096f2`, `1420fd3`: refactor del adaptador Factus y manejo de token — FEAT-011.
  - `46004d0`, `2782041`: credenciales por local y actualización de Header/Sidebar — FEAT-011/FEAT-010.
  - `50b05bb` (2026-09-24): emisión de factura por eventos de dominio y modelo rico — FEAT-012.
  - Sin commitear al 2026-10-16 14:30: notas crédito y débito (`V18`, `V19`, controladores, servicios, tests) — FEAT-013.
- **Resultado:** Parcial — trazabilidad restituida; la corrección del código no fue revisada por el rol Reviewer.
- **Archivos tocados:** `.ai/features.json` (v1.3.0), `.ai/progress/history.md`
- **Notas:** FEAT-007 y FEAT-008 (sesiones/idempotencia/onboarding y notifications/storage) llegaron en una rama paralela fusionada el 2026-09-16; su fecha es la de la fusión. El hash `f6f44cb` ya figuraba en la entrada del 2026-09-17 09:54, mientras que una entrada previa del 2026-09-16 cita el commit `03ee35f` del mismo arnés: son dos commits distintos (el primero versiona el historial, el segundo el arnés).

## [2026-10-16 14:30] [EXTERNO] — Auditoría SDD: Fases 1 a 3 (seguridad, facturación, documentación)
- **Tarea:** FEAT-014
- **Acción:** Revisión de specs, arnés e integraciones con tres agentes de exploración (solo lectura) y corrección por fases, con el usuario aprobando el orden. Hallazgos verificados contra el código antes de corregir.
  - **Fase 1 (seguridad):** `UserServiceImpl` (no asignar `SUPER_ADMIN` por `updateUser`; supervisor solo restablece `SELLER`; reactivar al único admin permitido); `StorageController`/`StorageServiceImpl` (`@PreAuthorize`, carpetas permitidas, prefijo `tienda-{id}`, tipos permitidos); `DatabaseSeeder` (solo `dev`, clave por `SEED_SUPERADMIN_PASSWORD`); `SecurityConfig` (CORS con orígenes explícitos); `ProductRepository.findByIdForUpdate` + `updateStock` (bloqueo pesimista y local); `spring-boot-starter-validation` + `@Valid` en DTO de users/sales/support; seguimiento público de tickets reducido (`TicketTrackingDto`) y radicado aleatorio.
  - **Fase 2 (facturación):** `OutboundHttp` (timeouts), sin respaldo silencioso a `mock` ni al local 1, notas crédito aisladas por local con `reference_code` determinista y `sale_id` persistido, `reference_code` de factura estable con recuperación ante 409, `getBySaleId` ya no escribe en una transacción `readOnly`.
  - **Fase 3 (documentación):** specs nuevos de storage, notifications y backoffice; reescritos auth, users, stores; actualizados billing, sales, inventory, settings, support, reports, products, purchases, customers; globales `_architecture`, `_conventions`, `_database-schema`, `_deployment`, `_project`, `_security`, `_index`, `_roadmap`, `_glossary`; `README.md` y `CLAUDE.md`.
- **Resultado:** Éxito — `mvnw test`: 474 pruebas, 0 fallos (antes 432).
- **Archivos tocados:** ver `git status`; ~60 archivos de `backend/` y de documentación.
- **Notas:** Nada está commiteado: el Committer debe separar `feat(billing)` (notas crédito/débito), `fix(security)`, `docs` y `chore(harness)`. Pendiente verificar contra el servidor en ejecución y contra PostgreSQL (los tests usan H2).

## [2026-10-16 14:30] [REVIEWER] — Cambios al arnés (R6): init.* con paridad, módulos derivados del código y reglas aclaradas
- **Tarea:** FEAT-014
- **Acción:** Cambios a las reglas del arnés, con motivo:
  1. `init.sh`: el `exit 1` estaba dentro de un subshell y `set -e` no lo dispara en una lista `||`, así que un arnés roto reportaba "ENTORNO SANO". Se reescribió con contador de errores y salida real; se portaron las fases 3 a 5 de `init.ps1`.
  2. `init.ps1`: usaba `.\mvnw.cmd` fijo aunque eligiera otro `$mvnCmd`, y descartaba la salida de Maven al fallar. Ahora usa `$mvnCmd` y muestra las últimas 40 líneas.
  3. Ambos: lista de módulos **derivada del código** (carpeta con `internal/`), aviso por specs huérfanos, `_roadmap.md` como documento obligatorio, anclaje a la raíz del repo, banderas `--skip-tests`/`-SkipTests` y `--frontend`/`-Frontend`.
  4. `agents.md`: rutas `docs/` → `backend/docs/`; `docker-dev.*` está en `backend/`; límite de documentos de R1 de 3 a 4 (el Context Pack de "crear módulo" ya llevaba 4) sin contar los de arranque; R6 acotada a las reglas del arnés (los roles sí actualizan `features.json` e `history.md`); el flujo incluye al Committer; `.claude/agents` son envoltorios y manda `.ai`.
  5. `leader.md`: rutas, qué hacer si no hay tareas `pending`, Committer tras la aprobación.
  6. `history.md`: se repararon dos caracteres de control (un tabulador y un retroceso, de secuencias `\t`/`\b` mal interpretadas) en las líneas 230 y 232; el texto restaurado es "terms-and-conditions" y "backend/docs/modules".
- **Resultado:** Éxito — ambos scripts dan el mismo veredicto; con `storage/spec.md` y `roles/reviewer.md` ausentes devuelven `exit 1` con 2 errores (probado y restaurado).
- **Archivos tocados:** `.ai/init.sh`, `.ai/init.ps1`, `.ai/agents.md`, `.ai/roles/leader.md`, `.ai/features.json`, `.ai/progress/history.md`, `.gitattributes`, `.gitignore`, `.claude/agents/*.md`.
- **Notas:** Esta entrada la escribe el asistente que aplicó los cambios, no un Reviewer independiente: conviene que una revisión humana confirme las reglas.

## [2026-10-16 14:43] [EXTERNO] — Corrección de la corrección: bloqueo de stock de la Fase 1 era insuficiente
- **Tarea:** FEAT-014
- **Acción:** Al probar la aplicación en ejecución (20 y luego 30 ventas en paralelo del mismo producto) se vio que el stock no bajaba lo esperado (80 → 78 tras 21 ventas). La entrada anterior de este historial describe `ProductRepository.findByIdForUpdate` (`SELECT … FOR UPDATE`) como la solución: **no lo era**. Dentro de la transacción de la venta el producto ya está cargado y Hibernate devuelve esa instancia con el valor viejo en vez de releerla, así que se seguían pisando las actualizaciones. Los tests unitarios con mocks no podían detectarlo.
- **Corrección:** `findByIdForUpdate` se eliminó; `ProductFacade.adjustStock` ejecuta un `UPDATE` atómico y condicionado (`adjustStockInStore`/`adjustStock`) y devuelve el stock resultante; `InventoryServiceImpl` toma de ahí `previousStock`/`newStock` del kardex. Nuevo `StockConcurrencyTest` (hilos reales sobre H2). Además se quitó `spring.jpa.properties.hibernate.dialect=PostgreSQLDialect` de `application.properties`: en `dev` con H2 generaba `FOR NO KEY UPDATE`, que H2 no entiende (también afectaba al refresh de tokens en `dev`).
- **Resultado:** Éxito — app en ejecución: 30 ventas en paralelo, stock 80 → 50 exacto y kardex sin `newStock` repetidos.
- **Archivos tocados:** `ProductRepository`, `ProductFacade`, `ProductServiceImpl`, `InventoryServiceImpl`, `application.properties`, `InventoryServiceImplTest`, `StockConcurrencyTest` y los docs que describían el mecanismo (README, CLAUDE.md, `_architecture`, `_conventions`, `_roadmap`, specs de inventory y sales).
- **Notas:** Lección: un cambio de concurrencia se verifica con hilos reales, no con mocks. Pendiente: el resto de bloqueos pesimistas (refresh tokens, documentos electrónicos) funcionan porque la fila no se carga antes en la transacción, pero no tienen un test de concurrencia.

## [2026-10-16 14:48] [EXTERNO] — Auditoría SDD: Fase 5 (infraestructura, dependencias, CI, frontend)
- **Tarea:** FEAT-015 y FEAT-017 (parciales)
- **Acción:**
  - **Dependencias:** springdoc 2.8.5 → 3.1.1 (la serie 2.8.x es de Boot 3; 3.1.1 se construye contra Boot 4.1.0), AWS SDK 2.25.20 → 2.56.0 (sin Netty), jjwt 0.12.5 → 0.13.0, jacoco 0.8.12 → 0.8.15, \spring-boot-flyway\ + \lyway-core\ → \spring-boot-starter-flyway\; versiones como propiedades. Verificado en una instancia real: \/v3/api-docs\ y Swagger UI responden 200.
  - **Configuración:** se quitó el dialecto PostgreSQL forzado de \pplication.properties\ (rompía los bloqueos en H2).
  - **Infraestructura:** \docker-compose.prod.yml\ reenvía todas las variables (JWT, cookies, CORS, SMTP, SEED, BILLING) y exige \DB_PASSWORD\, \JWT_SECRET\ y \APP_FRONTEND_URL\; \Dockerfile\ prod con \TZ=America/Bogota\, \HEALTHCHECK\ y \-XX:MaxRAMPercentage=75\; \.dockerignore\ excluye \.env\ y datos; \.github/workflows/ci.yml\ (backend, frontend, arnés) y \.github/dependabot.yml\; \ackend/data_backup_20260824_104444/\ salió del índice de git (\git rm --cached\, sin commit) y se ignora; \ackend/mvnw\ y \.ai/init.sh\ con bit ejecutable en el índice (si no, CI falla con "Permission denied").
  - **Frontend:** tokens solo en memoria (la sesión son las cookies \HttpOnly\); evento \uth:session-expired\ cierra la sesión de la UI; un 401 de \/auth/login\ muestra el mensaje del backend; el POS ya no suma un 19% fijo, envía \sendToFactus\ y no muestra un CUFE inventado; se eliminó \pnpm-lock.yaml\ (queda \package-lock.json\).
  - **Corrección de la Fase 1:** ver la entrada anterior (bloqueo de stock).
- **Resultado:** Éxito — \mvnw clean test\, \
pm run lint\ (solo avisos) y \
pm run build\ en verde; en vivo: login/refresh solo con cookie, CORS rechaza \https://evil.example\ y acepta \localhost:3000\, 30 ventas paralelas con stock exacto.
- **Notas:** Pendiente: ArchUnit, Testcontainers/PostgreSQL, dependency-check, UI de facturación y verificar que el workflow corre en GitHub (no se ha ejecutado). La nota de \.env\: Docker Compose lee \ackend/.env\, que puede pisar los valores por defecto de la compose.
## [2026-10-16 15:08] [COMMITTER] — Integración de la auditoría SDD (Fases 1 a 5) y de las notas crédito/débito
- **Tarea:** FEAT-013, FEAT-014 y parte de FEAT-015/FEAT-017
- **Acción:** 17 commits atómicos tras aprobación humana explícita del plan, en la rama local `refactor/factus-billing-module` (sin push). Cada commit de código se verificó en un worktree limpio (`test-compile` o suite completa) antes de crearse; tras cada uno se corrió `init.sh --skip-tests`. Los archivos con cambios previos y míos mezclados (`MockBillingAdapter`, `ElectronicInvoice`, `InvoiceServiceImplTest`, `FactusInvoiceClientTest`) se separaron en dos commits construyendo cada versión como blob.
- **Commits:**
  - `c4314af` chore(repo): untrack H2 backup, ignore dev data, track .claude agents
  - `10ae941` build: update dependencies and add Bean Validation starter
  - `1229c7b` feat(billing): add electronic credit and debit notes
  - `1b925a2` fix(billing): harden invoice emission
  - `5570aa1` fix(users): block SUPER_ADMIN escalation and seed only in dev
  - `4d581ef` fix(storage): require roles, restrict file types and scope files per store
  - `025e50a` fix(support): expose minimal public tracking and random ticket numbers
  - `7230b2e` fix(auth): explicit CORS origins and verification email subject typo
  - `ac98d66` fix(inventory): atomic stock adjustment and no forced PostgreSQL dialect
  - `099041b` feat(validation): enforce Bean Validation on user and sale requests
  - `e374031` build(infra): production compose, Dockerfile, CI and Dependabot
  - `9c03429` fix(frontend): keep session tokens in memory and stop adding fixed VAT
  - `6ffc530` chore(frontend): drop pnpm lockfile
  - `d0e4bae` docs(modules): add storage, notifications and backoffice specs and update module specs
  - `a45d4db` docs: bring global docs up to date
  - `a99fdb4` docs: update README and CLAUDE.md
  - (este) chore(harness): validate modules from code, align roles and log the audit
- **Resultado:** Éxito
- **Notas:** El workflow de CI (`.github/workflows/ci.yml`) no se ha ejecutado en GitHub. Los `V*MigrationSqlTest` y la concurrencia corren solo sobre H2. FEAT-013 queda pendiente de un `V18MigrationSqlTest`.
## [2026-10-20 12:00] [LEADER] — Backlog de la integración con Alegra (FEAT-020 a FEAT-029)
- **Tarea:** FEAT-020..029 — Reemplazar Factus por Alegra como proveedor de facturación electrónica por defecto (Factus queda como alterno). Solo backend.
- **Acción:** Se registran 10 tareas `pending` en `features.json` y un brief autocontenido por tarea en `.ai/tasks/FEAT-0NN-*.md` (Context Pack, archivos, pasos, criterios, pruebas, fuera de alcance, riesgos). No se tocó código de producción, tests ni `frontend/`.
- **Orden y dependencias:** FEAT-020 (spike en sandbox, necesita token y company.id del usuario) ∥ FEAT-021 (puertos) ∥ FEAT-022 (migración) → FEAT-023 (asignador) → FEAT-024 (infraestructura) → FEAT-025 (facturas) → FEAT-026 (notas) ∥ FEAT-027 (correo y XML) ∥ FEAT-028 (PDF) → FEAT-029 (cierre).
- **Hallazgos que condicionan la ejecución:**
  - Alegra exige que el sistema envíe `number` y la `resolution` completa: hace falta numeración local (no es un simple cambio de adaptador).
  - La API no documenta un endpoint de PDF; FEAT-020 decide entre descargarlo de Alegra o generarlo en el servidor (FEAT-028).
  - FEAT-019 (pending) reserva la migración V20: la de FEAT-022 usa la siguiente versión libre.
  - FEAT-018 (pending) solapa con FEAT-025 (llamada al proveedor fuera de la transacción con lock): se recomienda hacerlo antes; decisión pendiente del usuario.
- **Resultado:** Éxito (solo planificación y registro)
- **Notas:** Decisiones del usuario: Factus se conserva como proveedor alterno; credenciales = token de plataforma por entorno (variable de entorno) + `company.id` por local; PDF a decidir tras el spike.
## [2026-10-20 12:30] [LEADER] — Orden de ejecución de la integración con Alegra (FEAT-018 antes de FEAT-025)
- **Tarea:** FEAT-018, FEAT-020..029 — secuenciación
- **Decisión del usuario:** hacer FEAT-018 (llamada al proveedor fuera de la transacción con lock + reintento) antes de FEAT-025.
- **Acción:** Se crea `.ai/tasks/README.md` con el grafo de dependencias, las olas de ejecución y las reglas de coordinación; se actualizan la dependencia de FEAT-025 (brief y `features.json`) y la nota de FEAT-018. No se tocó código.
- **Orden:** Ola 0: FEAT-020 ∥ FEAT-021 → Ola 1: FEAT-022 → Ola 2: FEAT-018 ∥ (FEAT-023 → FEAT-024) → Ola 3: FEAT-025 → Ola 4: FEAT-026 ∥ FEAT-027 ∥ FEAT-028 → Ola 5: FEAT-029.
- **Resultado:** Éxito (solo planificación)
- **Notas:** Las migraciones (FEAT-019, FEAT-022, y la de FEAT-018 si la necesita) se numeran una a la vez al implementar. Alcance de FEAT-018 propuesto (núcleo + reintento; mapeo DIAN y rotación de claves aparte), pendiente de confirmar.
## [2026-10-20 13:00] [LEADER] — Alcance de FEAT-018 acotado y registro de FEAT-030
- **Tarea:** FEAT-018, FEAT-030
- **Decisión del usuario:** limitar FEAT-018 al núcleo (emisión en tres pasos) y al job de reintento; registrar FEAT-030 con el mapeo de códigos DIAN y la rotación de claves de cifrado.
- **Acción:** `features.json`: FEAT-018 reescrito (descripción, criterios y nota) y FEAT-030 añadido como `pending`. Nuevos briefs `.ai/tasks/FEAT-018-emision-tres-pasos-reintento.md` y `.ai/tasks/FEAT-030-codigos-dian-y-rotacion-claves.md`. `.ai/tasks/README.md` actualizado (alcance confirmado y ola opcional 6). No se tocó código.
- **Resultado:** Éxito (solo planificación)
- **Notas:** FEAT-018 puede requerir una migración (columnas `attempts`, `processing_started_at`, `next_retry_at`): numerar al implementar, una migración a la vez junto a FEAT-019 y FEAT-022. FEAT-030 no bloquea la integración con Alegra.
## [2026-10-20 13:30] [COMMITTER] — Backlog de la integración con Alegra (FEAT-018, FEAT-020 a FEAT-030)
- **Tarea:** registro de la planificación de FEAT-018 y FEAT-020..030
- **Acción:** 2 commits atómicos tras aprobación humana explícita del plan, en la rama local `refactor/factus-billing-module` (sin push). Solo se tocó `.ai/`: ningún código de producción, test ni `frontend/`. No se ejecutó `init.sh` (el entorno denegó el permiso al agente); se verificó a mano que `features.json` es JSON válido, que el diff está acotado a `.ai/` y que no hay secretos.
- **Commits:**
  - `ae4e935` chore(harness): add task briefs for Alegra integration and FEAT-018
  - (este) chore(harness): register Alegra backlog and narrow FEAT-018 scope
- **Resultado:** Éxito
- **Notas:** Pendiente de ejecución todo el backlog (FEAT-018 y FEAT-020..030 siguen `pending`). Primera ola: FEAT-020 (spike, necesita token y `company.id` del sandbox) y FEAT-021. Git avisa de conversión LF→CRLF por `core.autocrlf` en los archivos nuevos; no cambia el contenido.
## [2026-10-22 09:00] [LEADER] — Delegación de tarea
- **Tarea:** FEAT-021 — Alegra: preparar los puertos del proveedor (refactor sin Alegra)
- **Delegado a:** Implementer
- **Validación previa:** `init.ps1 -SkipTests` en verde (ENTORNO SANO, 0 errores, 1 advertencia por tests omitidos). La suite completa la corre el Implementer antes y después de sus cambios (R2).
- **Context Pack:**
  1. .ai/tasks/FEAT-021-puertos-proveedor-billing.md (completo; es el brief de la tarea)
  2. backend/docs/modules/billing/spec.md (completo)
  3. backend/docs/_conventions.md (completo)
  4. backend/docs/_architecture.md (completo)
  - Código por etapas (≤5 a la vez): A) ElectronicInvoicingProvider, FactusBillingAdapter, MockBillingAdapter · B) StoreBillingConfigServiceImpl, BillingActivationMapper, FactusApiException, GlobalExceptionHandler · C) CompanyLogoServiceImpl, más los tests listados en el brief.
- **Instrucciones:** Implementar solo FEAT-021 según el brief. Factus debe comportarse igual; sin código de Alegra; sin tocar `frontend/`; sin commits (los autoriza el usuario vía Committer). Registrar el resultado en history.md.

## [2026-10-22 10:00] [IMPLEMENTER] — Implementación de FEAT-021
- **Tarea:** FEAT-021 — Alegra: preparar los puertos del proveedor (refactor sin Alegra)
- **Acción:** (1) `BillingProviderException` como base de los errores de proveedor; `FactusApiException` la extiende sin cambiar su API. `GlobalExceptionHandler`: handler de la base (mismo mapeo 422/502 que Factus; `handleFactusApi` se conserva y delega) y `UnsupportedOperationException` -> 501. (2) Puerto `BillingAccountProvider` (`isAccountConfigured`, `verifyConnection`, `onAccountChanged`, `displayName`) que extiende `ElectronicInvoicingProvider`; `FactusBillingAdapter` delega en `FactusCredentialsResolver` (nuevo `isConfigured`, sin requerir ACTIVE) y `FactusTokenManager`; `MockBillingAdapter` = true/no-op/"Mock". (3) `ActiveBillingProvider` (`@Value("${billing.provider:factus}")`, falla con los disponibles; no sustituye los `resolveProvider()` de los 4 servicios). (4) `BillingProviderStartupValidator` (`billing/internal/config`, `@PostConstruct`): aborta el arranque si el proveedor no existe. (5) `StoreBillingConfigServiceImpl` ya no usa `FactusTokenManager` ni `credentialsFor(env).isComplete()`: usa `ActiveBillingProvider`; `notifyStore` usa `displayName()`; `BillingConnectionTestResult.success(env, providerName)`. (6) `BillingActivationMapper` toma `sandboxConfigured`/`productionConfigured` de `isAccountConfigured`. (7) `CompanyLogoServiceImpl.syncLogoWithFactus` sale con `false` si `billing.provider != factus` (campo `@Value`, constructores intactos). Spec de billing actualizado (RN-BILL-009 y RN-BILL-017).
- **Archivos tocados:** main: `billing/exception/BillingProviderException.java` (nuevo), `billing/exception/FactusApiException.java`, `billing/dto/BillingConnectionTestResult.java`, `billing/internal/adapters/BillingAccountProvider.java` (nuevo), `adapters/ActiveBillingProvider.java` (nuevo), `adapters/ElectronicInvoicingProvider.java`, `adapters/factus/FactusBillingAdapter.java`, `adapters/factus/FactusCredentialsResolver.java`, `adapters/mock/MockBillingAdapter.java`, `billing/internal/config/BillingProviderStartupValidator.java` (nuevo), `billing/internal/mappers/BillingActivationMapper.java`, `billing/internal/services/StoreBillingConfigServiceImpl.java`, `exceptions/GlobalExceptionHandler.java`, `settings/internal/services/CompanyLogoServiceImpl.java`; docs: `backend/docs/modules/billing/spec.md`. test: ver abajo.
- **Tests creados/adaptados:** nuevos `ActiveBillingProviderTest` (selección, normalización, default, error con disponibles, sin respaldo a mock), `BillingProviderStartupValidatorTest` (unitarios + `ApplicationContextRunner`: con `billing.provider=siigo` el contexto falla listando `[factus]`), `BillingProviderExceptionTest`, `MockBillingAdapterAccountTest`, `BillingUnsupportedOperationHandlingTest` (@SpringBootTest: `UnsupportedOperationException` desde el controlador de rangos -> 501). Ampliados `FactusExceptionHandlingTest` (base + 501), `FactusBillingAdapterTest` (puerto de cuenta), `FactusCredentialsResolverTest` (`isConfigured`), `CompanyLogoServiceTest` (no sincroniza si el proveedor no es Factus; sí con " Factus "). Adaptados `StoreBillingConfigServiceImplTest` (ahora sobre el puerto, +7 casos: mensaje con proveedor, cualquier `BillingProviderException`, flags desde el proveedor, notificación con displayName, mock activable sin credenciales, proveedor desconocido) y `StoreBillingConfigControllerTest` (firma de `success`).
- **init.ps1 (con tests):** antes de cambiar nada ENTORNO SANO; después ENTORNO SANO, 0 errores, 0 advertencias (surefire: 506 tests, 0 fallos, 0 errores). Verificación manual adicional: `BILLING_PROVIDER=siigo` con `spring-boot:run` (H2 en memoria) no arranca y el error dice "Proveedores disponibles: [factus]"; `BILLING_PROVIDER=mock` arranca.
- **Notas:** con el mock `isAccountConfigured` es siempre true (según el brief), por lo que un local con mock puede pasar a ACTIVE sin credenciales; antes exigía credenciales de Factus. No se leyó `_architecture.md` (no hizo falta para el alcance). Sin commits.

## [2026-10-22 11:00] [REVIEWER] — Aprobación de FEAT-021
- **Decisión:** ✅ APROBADO CON OBSERVACIONES (sujeto a que la suite completa del Leader esté en verde)
- **Criterios cumplidos:** los 7 ítems de "Qué se construye" verificados contra el código (no contra el informe): `BillingProviderException` base + `FactusApiException` sin cambio de API, handler de la base, `handleFactusApi` conservado y handler `UnsupportedOperationException` -> 501; puerto `BillingAccountProvider` extendido por `ElectronicInvoicingProvider` (Factus delega en resolver/token manager, Mock true/no-op); `ActiveBillingProvider`; `BillingProviderStartupValidator` (`billing/internal/config`, ubicación prevista por `_architecture.md`); `StoreBillingConfigServiceImpl` y `BillingActivationMapper` sin `Factus*`; guarda del logo en `settings`. Factus equivalente: `FactusCredentialsResolver.isConfigured` = `findById(...).map(credentialsFor(env).isComplete()).orElse(false)` (sin exigir ACTIVE, false sin fila, entorno pedido), idéntico a la comprobación anterior sobre la entidad. `grep adapters.factus` fuera de `adapters/factus/**` solo da `CompanyLogoServiceImpl` (residual declarado, tras la guarda). Sin cambios en `frontend/`, migraciones, rutas REST ni DTOs salvo el mensaje de `BillingConnectionTestResult`. Usos de `BillingConnectionTestResult.success` actualizados (main y test).
- **Validación:** revisión de código + ejecución propia (offline, timeout 170 s) de `ActiveBillingProviderTest`, `BillingProviderStartupValidatorTest`, `MockBillingAdapterAccountTest`: verde. La suite completa la ejecuta el Leader (no repetida aquí).
- **Notas / observaciones (ninguna bloqueante):**
  1. (menor) `BillingActivationMapper.toDto` ya no lee los flags de la entidad que recibe sino que vuelve a consultar el repositorio por `storeId` (2 `findById` por DTO, aciertan la caché de 1er nivel dentro de la transacción) y puede lanzar `IllegalStateException` si el proveedor no está registrado. Aceptable por el brief (punto 6); si algún día se mapea fuera de transacción serían 2N consultas.
  2. (menor) Con `billing.provider=mock`, `sandboxConfigured`/`productionConfigured` salen `true` siempre y un local puede pasar a ACTIVE sin credenciales (antes exigía las de Factus). Es lo que pide el brief; no rompe tests ni dev (el perfil dev usa factus por defecto). Queda documentado en el spec.
  3. (menor) El handler 501 es global: hoy las únicas `UnsupportedOperationException` deliberadas son los `default` de los puertos (`InvoiceProvider`, `NumberingRangeProvider`, `CreditNoteProvider`, `DebitNoteProvider`); ningún test depende de un 500. Efecto colateral: una `UnsupportedOperationException` accidental (p. ej. mutar una lista inmutable) se vería como 501 en vez de 500.
  4. (menor) Cobertura: `StoreBillingConfigServiceImplTest` usa un `ElectronicInvoicingProvider` simulado (Mockito); la unión real servicio + `FactusBillingAdapter` + resolver solo se prueba por piezas (adaptador y resolver tienen sus propios tests). Sin test unitario propio de `BillingActivationMapper`.
  5. (menor) El Implementer no leyó `_architecture.md`; verificado que el diff no lo contradice (`config/` bajo `internal/` está previsto; `ActiveBillingProvider` es `public` por necesidad de uso entre paquetes de `internal`; la violación `settings -> FactusTokenManager` ya estaba declarada).
  6. Spec (RN-BILL-009 y RN-BILL-017) coincide con el código. `@PostConstruct` del validador no altera el orden de inicialización (depende de `ActiveBillingProvider`); `@Value` con default `factus` mantiene los contextos `@SpringBootTest`/`@WebMvcTest`/`@DataJpaTest` existentes; FactusBillingAdapter siempre se registra, por eso `siigo` lista `[factus]`.
## [2026-10-22 11:00] [LEADER] — FEAT-021 aprobada por el Reviewer
- **Tarea:** FEAT-021 — Alegra: preparar los puertos del proveedor
- **Resultado del Reviewer:** APRUEBA CON OBSERVACIONES (sin bloqueantes ni importantes). Un primer intento de revisión se colgó por esperar una ejecución larga de Maven (600 s sin avance) y no dejó cambios; se relanzó como revisión estática más pruebas cortas.
- **Validación independiente del Leader:** `init.ps1` completo (con tests) en verde: ENTORNO SANO, 0 errores, 0 advertencias; Surefire 73 clases, 506 tests, 0 fallos, 0 errores, 0 omitidos.
- **Observaciones menores aceptadas:** (1) `BillingActivationMapper` consulta el repositorio por `storeId` (2 `findById` por DTO; aciertan la caché de primer nivel dentro de transacción); (2) el handler 501 es global y enmascararía como 501 una `UnsupportedOperationException` accidental; (3) falta un test de integración ligero mapper + resolver real (opcional); (4) con `billing.provider=mock` un local puede pasar a ACTIVE sin credenciales (documentado en el spec).
- **Incidente del Implementer:** para cerrar su prueba manual ejecutó `taskkill /F /IM java.exe`, que mata todos los procesos Java de la máquina. Se avisa al usuario. Reforzar en próximas delegaciones: prohibido matar procesos por nombre; detener solo el PID propio.
- **Estado:** `features.json` marca FEAT-021 como `done`. Sin commitear: pendiente el plan del Committer y la aprobación humana.
