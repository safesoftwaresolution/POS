<!-- doc-version: 1.3 | last-updated: 2026-10-02 -->
# Proyecto POS — Contexto General

## Resumen
Plataforma multi-tenant de API REST para gestión de ventas, inventario y facturación electrónica DIAN. Cada tienda (`store`) opera aislada de las demás (ver `_security.md` / `TenantContext`); dentro de una tienda, el modelo de negocio sigue siendo el de un local con una sola caja física.

## Stack Tecnológico
- **Backend**: Java 21, Spring Boot, Spring Security, Spring Data JPA, Flyway (esquema)
- **Frontend**: React 19, Vite, Tailwind CSS 4 (SPA sin lógica de negocio)
- **Almacenamiento de archivos**: Cloudflare R2 (S3-compatible)
- **Base de Datos**: PostgreSQL 16 (producción), H2 (perfil `dev` por defecto y tests)
- **Autenticación**: JWT (HMAC-SHA256, access token 15 min + refresh token rotativo)
- **Facturación Electrónica**: Factus API (Estándar DIAN Colombia)
- **Build**: Maven

## Roles del Sistema
| Rol | Perfil | Alcance |
|---|---|---|
| `SUPER_ADMIN` | Equipo de la plataforma SaaS (sin local) | Back office: locales y KYC, credenciales de facturación, categorías globales, textos legales, soporte, personal de plataforma |
| `ADMINISTRATOR` | Dueño de un local | Control total de su local: usuarios, reportes, configuración, facturación, rangos de numeración |
| `SUPERVISOR` | Encargado de tienda | Compras, inventario, catálogo, clientes, reintento de facturas |
| `SELLER` | Cajero en mostrador | Ventas, consulta de productos, registro de clientes, consulta de facturas |

## Flujo del Negocio
0. **Alta del local**: el dueño se auto-registra, verifica su correo, registra su local y sube documentos KYC; el `SUPER_ADMIN` lo aprueba y carga las credenciales de facturación electrónica del local.
1. **Abastecimiento**: Supervisor registra compra a proveedor → stock sube automáticamente.
2. **Venta**: Vendedor procesa venta en efectivo → stock baja automáticamente → ticket generado → emisión opcional a Factus (DIAN).
3. **Control**: Administrador consulta reportes de rentabilidad, stock y estado de facturación.

## Exclusiones Explícitas (Fuera de Alcance)
| Excluido | Razón |
|---|---|
| Notas crédito/débito | Requiere contabilidad avanzada |
| Mesas y reservas | No es software de restaurante |
| Despachos/domicilios | Entrega inmediata en mostrador |
| Múltiples monedas | Moneda local única |
| Múltiples cajas físicas por tienda | Una sola caja por local |

**Ya NO están excluidos** (implementados desde entonces, ver `V8`–`V10` en `db/migration/` y `_roadmap.md`): devoluciones de venta (`sales` — notas de devolución, no notas crédito contables), múltiples métodos de pago (`sales`, catálogo dinámico vía `settings`) y múltiples sucursales (multi-tenancy por `store_id`, ver `stores` y `_security.md`).

## Objetivos
- Procesamiento de ventas ágil con lector de código de barras
- Control de inventario en tiempo real
- Reportes básicos de rentabilidad y stock mínimo
- Trazabilidad: toda transacción asociada a usuario y rol
