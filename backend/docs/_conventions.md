<!-- doc-version: 1.1 | last-updated: 2026-10-02 -->
# Convenciones de Código

## Estructura de Paquetes (Modular Monolith)

El código Java del proyecto se divide verticalmente en **módulos** bajo el paquete raíz. Cada módulo consta de una API pública en su raíz y capas internas de implementación en el paquete `internal/`.

```
src/main/java/com/sciencebot/pos/
├── Application.java             ← Clase de arranque Spring Boot
│
├── config/                      ← Configuración técnica global (Seguridad JWT, CORS)
├── exceptions/                  ← Clases de excepciones y manejo global de errores
│
├── shared/                      ← Módulo transversal abierto (OPEN)
│   ├── Money.java               ← Value Object compartido
│   └── AuditInfo.java
│
└── [modulo]/                    ← Carpeta del módulo de negocio (ej. sales, products)
    ├── [Module]Facade.java      ← Interfaz pública de entrada al módulo
    ├── [Module]Dto.java         ← DTOs de salida públicos
    ├── Create[Module]Command.java ← DTOs de entrada públicos
    ├── [Module]CreatedEvent.java ← Eventos de dominio públicos
    │
    └── internal/                ← Implementación privada (invisible a otros módulos)
        ├── controllers/         ← Controladores REST HTTP (@RestController)
        ├── services/            ← Implementación de lógica de negocio y Facade
        ├── repositories/        ← Interfaces de Repositorio JPA
        └── entities/            ← Entidades JPA del módulo
```

---

## Reglas de Visibilidad e Inyección

1. **Encapsulación Estricta**: Todas las clases dentro de `internal/` deben ser declaradas sin modificador de acceso público (`package-private` o `class-private`) siempre que sea posible.
2. **Inyección de Dependencias**:
   - Entre clases del mismo módulo: Inyección directa vía constructores.
   - Entre módulos distintos: **Solo** se permite inyectar la interfaz pública `Facade` (ej. `@Autowired private InventoryFacade inventoryFacade`). Nunca se inyectan clases dentro del paquete `internal/` de otro módulo.
3. **Mapeo de Datos (DTOs)**:
   - Las entidades JPA (`@Entity`) nunca deben salir de su módulo ni ser expuestas a otros módulos o al exterior.
   - Toda comunicación exterior y cross-module utiliza DTOs inmutables (`record` en Java).

---

## Nombrado y Convenciones Java

| Elemento | Convención | Ejemplo |
|---|---|---|
| Clases e Interfaces | PascalCase | `ProductFacade`, `ProductServiceImpl` |
| Métodos y Variables | camelCase | `calculateTotalAmount()`, `quantityAvailable` |
| Constantes | UPPER_SNAKE_CASE | `MAX_LOGIN_ATTEMPTS` |
| Paquetes | minúsculas, singular | `com.sciencebot.pos.sales.internal` |

---

## Reglas por Capa Interna

### Controllers (`internal/controllers`)
- `@RestController` con mapeo de rutas `/api/v1/[modulo]`.
- Se comunican únicamente con el servicio interno del módulo.

### Services (`internal/services`)
- Implementan los métodos de la interfaz `Facade` pública del módulo.
- `@Transactional(readOnly = true)` para operaciones de lectura; `@Transactional` para escritura.

### Repositories (`internal/repositories`)
- Heredan de `JpaRepository<Entity, Long>`.

### Base de Datos y Migraciones
- Se prohíbe el uso de generación automática de tablas por JPA (`ddl-auto` configurado a `validate`).
- Todo cambio al esquema debe implementarse mediante migraciones SQL de Flyway en `src/main/resources/db/migration/`.

### Mapeo de Datos (Mappers)
- Se prohíbe realizar el mapeo de entidades JPA a DTOs (o viceversa) mediante métodos privados dentro de los servicios.
- Todo mapeo de datos debe estructurarse obligatoriamente mediante clases Mapper dedicadas dentro del paquete `internal/mappers/` de cada módulo (ej. `ProductMapper`).
- Estas clases Mapper deben ser marcadas con `@Component` e inyectarse vía constructor en los servicios que las requieran.

---

## Tests


- Directorio: `src/test/java/com/sciencebot/pos/`
- Framework: JUnit 5 + Mockito + ArchUnit (para validación estructural de Spring Modulith).
- Cobertura: Testear de forma aislada cada módulo y verificar sus API públicas.

> **Excepción vigente — `billing`**: su API pública está organizada en subpaquetes `billing/api/` (`BillingFacade`), `billing/dto/` y `billing/exception/` en lugar de la raíz del módulo (reorganización del 2026-09-23). Está pendiente decidir si se generaliza al resto de módulos o `billing` vuelve al formato estándar (ver `_roadmap.md`). Los módulos nuevos siguen el formato estándar de arriba.
