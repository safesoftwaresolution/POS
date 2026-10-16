<!-- doc-version: 1.3 | last-updated: 2026-10-16 -->
# Proyecto POS — Contexto General

## Resumen
Plataforma multi-tenant de API REST para gestión de ventas, inventario y facturación electrónica DIAN. Cada tienda (`store`) opera aislada de las demás (ver `_security.md` / `TenantContext`); dentro de una tienda, el modelo de negocio sigue siendo el de un local con una sola caja física.

## Stack Tecnológico
- **Backend**: Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA, Flyway, `JdbcClient` (reportes)
- **Frontend**: React 19, Vite 8, Tailwind CSS 4 (SPA sin lógica de negocio)
- **Base de Datos**: PostgreSQL 16 (producción), H2 (desarrollo y tests)
- **Autenticación**: JWT HMAC (access token 15 min + refresh token rotativo), cookies `HttpOnly`
- **Facturación Electrónica**: Factus API (Estándar DIAN Colombia)
- **Almacenamiento de archivos**: Cloudflare R2 (API S3)
- **Build**: Maven

## Roles del Sistema
| Rol | Perfil | Alcance |
|---|---|---|
| `SUPER_ADMIN` | Equipo de la plataforma SaaS (sin local) | Back office: locales y KYC, categorías globales, textos legales, soporte, credenciales y habilitación de facturación, operadores |
| Administrador | Dueño del negocio | Control total: usuarios, reportes, configuración, facturación |
| Supervisor | Encargado de tienda | Compras, inventario, catálogo, clientes, reintento de facturas |
| Vendedor | Cajero en mostrador | Ventas, consulta de productos, registro de clientes, consulta de facturas |

## Flujo del Negocio
1. **Abastecimiento**: Supervisor registra compra a proveedor → stock sube automáticamente.
2. **Venta**: Vendedor procesa venta en efectivo → stock baja automáticamente → ticket generado → emisión opcional a Factus (DIAN).
3. **Control**: Administrador consulta reportes de rentabilidad, stock y estado de facturación.

## Exclusiones Explícitas (Fuera de Alcance)
| Excluido | Razón |
|---|---|
| Mesas y reservas | No es software de restaurante |
| Despachos/domicilios | Entrega inmediata en mostrador |
| Múltiples monedas | Moneda local única |
| Múltiples cajas físicas por tienda | Una sola caja por local |

**Ya NO están excluidos** (implementados desde entonces, ver `V8`–`V19` en `db/migration/` y `_roadmap.md`): notas crédito y débito electrónicas (`billing`, `V18`/`V19`), devoluciones de venta (`sales` — notas de devolución, no notas crédito contables), múltiples métodos de pago (`sales`, catálogo dinámico vía `settings`) y múltiples sucursales (multi-tenancy por `store_id`, ver `stores` y `_security.md`).

## Objetivos
- Procesamiento de ventas ágil con lector de código de barras
- Control de inventario en tiempo real
- Reportes básicos de rentabilidad y stock mínimo
- Trazabilidad: toda transacción asociada a usuario y rol
