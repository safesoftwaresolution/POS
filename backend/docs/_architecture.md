<!-- doc-version: 1.1 | last-updated: 2026-10-16 -->
# Arquitectura del Backend: Monolito Modular

El sistema es un **Monolito Modular** con **división vertical**: un único proceso JVM dividido internamente en módulos acotados (Bounded Contexts). Los 18 módulos están en `com.sciencebot.pos`: `auth`, `users`, `categories`, `products`, `inventory`, `customers`, `suppliers`, `purchases`, `sales`, `billing`, `reports`, `settings`, `stores`, `legal`, `support`, `notifications`, `storage` y `backoffice`.

```
                  ┌────────────────────────────────────────────────┐
                  │                 API REST / JSON                │
                  └───────┬────────────────────────────────┬───────┘
                          │                                │
                          ▼                                ▼
              ┌──────────────────────┐          ┌──────────────────────┐
              │  MÓDULO: sales       │          │  MÓDULO: inventory   │
              │  (Vertical Slice)    │          │  (Vertical Slice)    │
              │  + API Pública       │          │  + API Pública       │
              │  + Lógica Interna    │          │  + Lógica Interna    │
              └───────────┬──────────┘          └───────────┬──────────┘
                          └────────────────┬───────────────┘
                                           │ JPA / Hibernate (JdbcClient en reports)
                                           ▼
                  ┌────────────────────────────────────────────────┐
                  │   PostgreSQL 16 (prod) / H2 (dev, tests)       │
                  └────────────────────────────────────────────────┘
```

---

## Estructura Modular y Aislamiento

1. **API Pública del Módulo**: raíz del paquete del módulo (ej. `com.sciencebot.pos.sales.*`). Contiene la interfaz `Facade`, los DTOs/Commands de entrada y salida y los eventos de dominio que expone a otros módulos. **Excepción**: `billing` agrupa su API pública en subpaquetes `billing/api` (`BillingFacade`), `billing/dto` y `billing/exception` por tamaño. Es la única; está pendiente decidir si se generaliza o se devuelve a la raíz (`_roadmap.md`).
2. **Lógica Interna**: subpaquete `internal/` (`controllers/`, `services/`, `repositories/`, `entities/`, `mappers/` y, según el módulo, `adapters/`, `listeners/`, `config/`). Ningún otro módulo debe importarla.

> **Estado de la regla**: hoy no hay ArchUnit ni Spring Modulith en el proyecto que la haga cumplir, y casi todas las clases de `internal/` son `public`. La regla se respeta por convención y revisión. Violación conocida: `settings` importa `billing.internal.adapters.factus.FactusTokenManager`.

### Excepciones declaradas al aislamiento de datos
- `reports` usa SQL de solo lectura (`JdbcClient`, parámetros nombrados) sobre tablas de otros módulos (CQRS).
- `backoffice` (`OnboardingStatsDao`) consulta `users` y `stores` con SQL de solo lectura.

---

## Comunicación entre Módulos

### 1. Llamada Síncrona Directa (Vía Facade)
Un módulo invoca a otro **únicamente** inyectando su interfaz `Facade`.
- *Ejemplo*: `sales` inyecta `ProductFacade` e `InventoryFacade` para validar y descontar stock.

### 2. Eventos de Dominio (in-process)
Se usan para desacoplar flujos secundarios que no deben afectar la transacción principal.
- *Ejemplo real*: al confirmarse una venta, `sales` publica `SaleCompletedEvent` con `ApplicationEventPublisher`. `billing` lo escucha en `ElectronicInvoiceEventListener` con `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` y emite la factura electrónica. Una falla de Factus nunca revierte ni retrasa la venta.
- El descuento de stock **no** es por evento: es síncrono, dentro de la transacción de la venta, con un `UPDATE` atómico y condicionado.

---

## Multi-tenancy (alcance por local)
`JwtAuthenticationFilter` extrae `storeId` del JWT y lo guarda en `TenantContext` (`ThreadLocal`, `config/tenant`) durante la solicitud; se limpia en `finally`. Todo servicio con datos de un local filtra por `TenantContext.getStoreId()`. El aislamiento es **manual** (cada consulta/servicio), no hay `@Filter` de Hibernate ni RLS: un servicio que olvide filtrar expone datos de otros locales. Las operaciones de facturación **exigen** local (sin respaldo al local 1). `categories` es global; `SUPER_ADMIN` no tiene local.

---

## Concurrencia y Transacciones

- **Consistencia transaccional**: una transacción de negocio síncrona ocurre dentro de un módulo; los flujos que cruzan módulos y toleran consistencia eventual usan eventos.
- **Stock atómico**: `ProductFacade.adjustStock` ejecuta un solo `UPDATE … SET quantity_available = quantity_available + :delta WHERE … AND quantity_available + :delta >= 0` (`ProductRepository.adjustStockInStore`); la fila queda bloqueada hasta el commit, así que ventas, compras y ajustes concurrentes del mismo producto se serializan y nunca queda negativo. **No** vale un `SELECT … FOR UPDATE` seguido de modificar la entidad: dentro de la venta el producto ya está cargado y Hibernate devuelve la instancia vieja, perdiendo actualizaciones (`StockConcurrencyTest` lo cubre). El kardex toma el stock resultante de la BD, no de la lectura inicial. Hay bloqueo pesimista de lectura en `refresh_tokens` y en las tablas de documentos electrónicos (por `reference_code` / venta), donde la fila no estaba cargada antes.
- **Idempotencia**: `Idempotency-Key` en ventas y compras (`UNIQUE` en BD), `reference_code` determinista en documentos electrónicos. Ver la memoria del proyecto.
- **Llamadas externas**: toda llamada saliente (Factus) usa `OutboundHttp.restClient()` con timeouts de conexión (5 s) y lectura (45 s).
- **Zona horaria**: el código usa `LocalDateTime.now()` (zona de la JVM) con columnas `TIMESTAMP` sin zona; los contenedores están en UTC. Pendiente fijar `America/Bogota` (`_roadmap.md`).
- **Aislamiento de datos**: un esquema SQL único; cada módulo es dueño exclusivo de sus tablas y no hace `JOIN` con las de otro bounded context (salvo las excepciones declaradas).
