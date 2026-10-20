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
  6. Implementación del módulo legal: CRUD completo de textos legales (privacy-policy, 	erms-and-conditions, etc.), versionamiento, toggle publish y endpoint público para lectura del texto vigente.
  7. Implementación del módulo support: radicación pública de PQRs y bugs del sistema con código de radicado automático (PQR-YYYYMMDD-NNNN / BUG-YYYYMMDD-NNNN), tracking público, búsqueda con filtros, métricas y resolución.
  8. Creación de especificaciones modulares spec.md en ackend/docs/modules/ para stores, legal, support y actualización de SSOT.
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
