---
name: pos-new-module
description: Scaffolda un módulo nuevo del backend (monolito modular, vertical slicing) respetando las convenciones de POS — Facade pública, DTOs/Commands, paquete internal/ encapsulado, migración Flyway. Usar cuando se vaya a crear un módulo de negocio nuevo bajo com.sciencebot.pos.
---

# Crear un módulo nuevo en el backend de POS

Este proyecto es un **monolito modular** (`backend/docs/_architecture.md`, `_conventions.md`). Antes de escribir código, lee esos dos archivos si no están ya en contexto — esta skill resume sus reglas aplicadas a la creación de un módulo, no las reemplaza.

## 1. Estructura de paquete obligatoria

```
com.sciencebot.pos.[modulo]/
├── [Modulo]Facade.java          ← interfaz pública, único punto de entrada para otros módulos
├── [Modulo]Dto.java             ← record de salida
├── Create[Modulo]Command.java   ← record de entrada (creación)
├── Update[Modulo]Command.java   ← record de entrada (edición), si aplica
├── [Modulo]CreatedEvent.java    ← evento de dominio, si otros módulos deben reaccionar async
└── internal/
    ├── controllers/   ← @RestController, mapea /api/v1/[modulo], solo habla con el service propio
    ├── services/      ← @Service, implementa el Facade; @Transactional(readOnly=true) en lecturas, @Transactional en escrituras
    ├── repositories/  ← extiende JpaRepository<Entity, Long>
    ├── entities/      ← @Entity — JAMÁS sale de este paquete
    └── mappers/       ← @Component, mapeo Entity↔Dto explícito (nunca métodos privados en el service)
```

Todo lo bajo `internal/` va sin modificador `public` cuando Java lo permita (package-private).

## 2. Reglas de dependencia entre módulos

- Para usar otro módulo, inyecta **solo su `Facade`** (ej. `InventoryFacade`). Nunca importes nada de `otromodulo.internal.*`.
- Si el nuevo módulo necesita reaccionar a algo de otro módulo sin acoplarse síncronamente, usa un evento de dominio (`ApplicationEventPublisher` / `@ApplicationModuleListener`), no una llamada directa.
- Si el módulo es multi-tenant (la mayoría de los de negocio: products, customers, suppliers, sales, purchases, inventory...), la entidad debe tener `store_id` y el service debe leer la tienda actual con `TenantContext.getStoreId()` (`com.sciencebot.pos.config.TenantContext`). Revisa si `categories` (global, sin `store_id`) o `products` (por tienda) es el precedente más parecido a tu caso antes de decidir.

## 3. Base de datos

- Nunca dependas de `ddl-auto` para crear tablas (en producción está en `validate`).
- Crea la migración siguiente en `backend/src/main/resources/db/migration/V{N}__Descripcion.sql` — revisa cuál es el número más alto actual antes de nombrarla, y nunca edites una migración ya aplicada.
- Si el módulo necesita datos base (catálogos, valores por defecto), sigue el patrón de seeding existente: un `@Component implements CommandLineRunner` en `internal/services/`, idempotente (verifica existencia antes de crear), que usa la `Facade` del propio módulo — ver `users/internal/services/DatabaseSeeder.java` como referencia. Si los datos son solo para desarrollo/demo, anota la clase con `@Profile("dev")`.

## 4. Checklist antes de dar por terminado el módulo

- [ ] `Facade` es la única clase pública referenciable desde otros módulos.
- [ ] Ninguna `@Entity` aparece en una firma de método fuera de `internal/`.
- [ ] El mapeo Entity↔Dto vive en `internal/mappers/`, no en el service.
- [ ] El controller solo delega al service del propio módulo.
- [ ] Migración Flyway agregada (si hay cambios de esquema) con el siguiente número secuencial.
- [ ] Doc del módulo creada/actualizada en `backend/docs/modules/[modulo]/spec.md` (formato: header `<!-- spec-version: X.Y | last-updated: YYYY-MM-DD -->`, Dependencias, Reglas de Negocio, Requerimientos Funcionales, API Endpoints, Modelo de Datos, Permisos — copia la estructura de un spec existente como `products` o `categories`) y agregado a la tabla de `backend/docs/_index.md`.
- [ ] Si el módulo introduce deuda técnica conocida o queda algo pendiente, anótalo en `backend/docs/_roadmap.md` en la fila del módulo correspondiente (ver skill `pos-doc-sync`).
