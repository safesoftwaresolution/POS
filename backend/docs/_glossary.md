<!-- doc-version: 1.0 | last-updated: 2026-07-08 -->
# Glosario

## Términos del Dominio
| Término | Definición |
|---|---|
| POS | Point of Sale. Interfaz de software y espacio físico donde se procesan transacciones |
| Stock | Cantidad disponible física de un producto para la venta |
| Stock Mínimo | Nivel de inventario de seguridad; por debajo genera alerta de reabastecimiento |
| Margen de Utilidad | `Precio_Venta - Precio_Compra` |
| Ticket | Comprobante de venta en papel térmico (58mm/80mm) |
| Cierre de Caja | Comparación del efectivo físico vs. total de ventas registradas |
| Merma | Mercadería dañada/vencida/perdida retirada del inventario |
| Kardex | Registro histórico cronológico de movimientos de un producto |

## Términos Técnicos
| Término | Definición |
|---|---|
| SSOT | Single Source of Truth. Documentación sin duplicidades ni contradicciones |
| ACID | Atomicity, Consistency, Isolation, Durability. Propiedades de transacciones BD |
| JWT | JSON Web Token. Autenticación stateless en API REST |
| RBAC | Role-Based Access Control. Permisos basados en roles |
| DTO | Data Transfer Object. Encapsula datos entre cliente y servidor |
| JPA | Java Persistence API. Estándar de persistencia ORM en Java |
| Borrado Lógico | Marcar registro como inactivo en lugar de eliminarlo físicamente |
| Tenant / Local | Un comercio de la plataforma SaaS (`stores`). Todo dato de negocio lleva su `store_id` |
| `SUPER_ADMIN` / `ADMINISTRATOR` / `SUPERVISOR` / `SELLER` | Roles del sistema, con ese nombre exacto (el del enum). `SUPER_ADMIN` es de la plataforma; los otros tres pertenecen a un local. En los specs "Admin" = `ADMINISTRATOR`, "Vendedor" = `SELLER` |
| KYC | Documentos legales que un local sube para ser aprobado (RUT, cámara de comercio, etc.) |
| Factus | Proveedor tecnológico autorizado ante la DIAN a través del cual se emiten los documentos electrónicos |
| CUFE / CUDE | Código único de factura electrónica (CUFE) o de documento electrónico (CUDE, notas crédito/débito) |
| Nota crédito / Nota débito | Documentos electrónicos que anulan o corrigen una factura (crédito) o cobran un valor adicional sobre ella (débito) |
| `reference_code` | Código propio que identifica un documento electrónico ante Factus; debe ser estable entre reintentos para no duplicarlo |
| Rango de numeración | Resolución DIAN que autoriza un prefijo y un consecutivo de facturas; el local elige cuál usa en cada entorno |
| Radicado | Número del ticket de soporte (PQR/bug); no es secuencial |
| Idempotency-Key | Header opcional de ventas y compras que evita registrar dos veces la misma operación ante un reenvío |
