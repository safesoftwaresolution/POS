<!-- doc-version: 1.1 | last-updated: 2026-10-02 -->
# Arquitectura del Backend: Monolito Modular

El sistema está diseñado bajo el estilo arquitectónico de **Monolito Modular (Modular Monolith)** con **División Vertical (Vertical Split)**. Toda la aplicación se ejecuta dentro de un único proceso JVM, pero está dividida internamente en módulos acotados y aislados que representan contextos delimitados (Bounded Contexts) del negocio.

```
                  ┌────────────────────────────────────────────────┐
                  │                 API REST / JSON                │
                  └───────┬────────────────────────────────┬───────┘
                          │                                │
                          ▼                                ▼
              ┌──────────────────────┐          ┌──────────────────────┐
              │  MÓDULO: sales       │          │  MÓDULO: inventory   │
              │  (Vertical Slice)    │          │  (Vertical Slice)    │
              │                      │          │                      │
              │  + API Pública       │          │  + API Pública       │
              │  + Lógica Interna    │          │  + Lógica Interna    │
              └───────────┬──────────┘          └───────────┬──────────┘
                          │                                │
                          └────────────────┬───────────────┘
                                           │ JPA / Hibernate
                                           ▼
                  ┌────────────────────────────────────────────────┐
                  │          Base de Datos Relacional SQL          │
                  └────────────────────────────────────────────────┘
```

---

## 🧱 Estructura Modular y Aislamiento

El proyecto sigue una división vertical estricta. Cada módulo encapsula sus controladores, servicios, repositorios y entidades en un subpaquete dedicado:

1. **API Pública del Módulo**: Ubicada en la raíz del paquete del módulo (ej. `com.sciencebot.pos.catalog.*`). Contiene la interfaz `Facade` (puerta de entrada), los DTOs de entrada/salida y los Eventos de Dominio que expone a otros módulos.
2. **Lógica Interna (Privada)**: Ubicada en el subpaquete `internal/` (ej. `com.sciencebot.pos.catalog.internal.*`). Todas sus clases de implementación, entidades JPA y repositorios son invisibles (`package-private`) para el resto de la aplicación.

---

## 🔄 Comunicación entre Módulos

La comunicación entre los módulos del sistema se realiza exclusivamente de dos formas para evitar dependencias circulares y alto acoplamiento:

### 1. Llamada Síncrona Directa (Vía Facade)
Un módulo puede invocar a otro de forma síncrona **únicamente** inyectando la interfaz `Facade` del módulo proveedor.
- *Ejemplo*: `sales` inyecta `InventoryFacade` para verificar si un producto tiene stock disponible antes de crear la transacción de venta.

### 2. Comunicación Asíncrona (Eventos de Dominio)
Para desacoplar flujos de negocio secundarios, los módulos emiten eventos de dominio in-process mediante `ApplicationEventPublisher`. Los módulos receptores escuchan estos eventos de manera asíncrona.
- *Ejemplo*: al confirmarse una venta, `sales` publica `SaleCompletedEvent` y `billing` lo atiende en `ElectronicInvoiceEventListener` (`@Async` + `@TransactionalEventListener(AFTER_COMMIT)`) para emitir la factura DIAN sin bloquear la venta. El descuento de stock, en cambio, es síncrono (vía `InventoryFacade`, con bloqueo pesimista).

---

## 📈 Concurrencia y Transacciones

- **Consistencia Transaccional**: Cada transacción de negocio síncrona debe ocurrir dentro de los límites de un único módulo. Si un flujo involucra múltiples módulos y requiere consistencia eventual, se debe preferir la publicación de eventos asíncronos.
- **Bloqueos Concurrentes**: Al descontar existencias físicas en el inventario, se utiliza **Pessimistic Locking** (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) en la base de datos para prevenir race conditions concurrentes (múltiples ventas del mismo producto al mismo tiempo).
- **Aislamiento de Datos**: Aunque se comparte un esquema relacional SQL único, los módulos no deben realizar consultas `JOIN` entre tablas que pertenezcan a distintos bounded contexts. Cada módulo es dueño exclusivo de sus tablas asociadas.

---

## 🏢 Multi-tenencia

Toda petición queda acotada a un local (`store`): `JwtAuthenticationFilter` extrae `storeId` del JWT y lo guarda en `TenantContext` (`config/tenant/`, un `ThreadLocal` que se limpia al terminar la petición). Las consultas por local leen `TenantContext.getStoreId()`; los listeners asíncronos deben fijarlo y restaurarlo explícitamente (ver `ElectronicInvoiceEventListener`). `categories` es el único catálogo deliberadamente global. Ver `_security.md`.

## 📖 Modelo de lectura (CQRS)

`reports` usa `JdbcClient` con SQL nativo (sin entidades JPA) para agregaciones de solo lectura.

## ⚙️ Paquete `config/`

Subpaquetes funcionales: `config/security` (JWT, filtros, CORS), `config/tenant` (`TenantContext`), `config/async` (ejecutores `@Async`) y `config/openapi` (Swagger/OpenAPI).
