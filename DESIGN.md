# PosBento — Design System & UI Specification

## 1. Product Identity & Philosophy

**PosBento** ("Tu negocio, en equilibrio") es un sistema moderno de Punto de Venta (POS), control de inventario en tiempo real y facturación electrónica oficial DIAN (Colombia) bajo una arquitectura multi-tenant y backoffice corporativo.

El diseño se basa en la estética **Bento Grid**:
- Organización modular en compartimentos rectangulares y cuadrados con bordes generosamente redondeados (`24px` / `rounded-3xl`).
- Jerarquía visual limpia donde cada tarjeta ("bento-card") tiene un propósito atómico y autónomo.
- Contraste sofisticado y legible con soporte nativo de **Modo Claro (Warm Stone / Neutral)** y **Modo Oscuro (Deep Obsidian / Charcoal)**.
- Acentos vibrantes en **Naranja Eléctrico / Llama (`#FF5520`)** que dirigen el foco de atención hacia acciones clave (cobro, agregar productos, alertas).

---

## 2. Paleta de Color y Tokens

### 2.1 Color Primario (Acento de Marca)
- **Primary**: `#FF5520` (Naranja Eléctrico Bento)
- **Primary Hover**: `#E64712`
- **Primary Subtle / Glow**: `rgba(255, 85, 32, 0.15)`
- **Primary Border**: `rgba(255, 85, 32, 0.3)`

### 2.2 Modo Claro (Warm Stone)
- **Background Page (`--bg-page`)**: `#F7F7F5` (Gris cálido suave)
- **Surface / Card (`--bg-card`)**: `#FFFFFF` (Blanco puro)
- **Card Border (`--border-card`)**: `#EAEAEA`
- **Text Primary (`--text-primary`)**: `#0A0A0A` (Casi negro con alto contraste)
- **Text Secondary (`--text-secondary`)**: `#7A7A7A`
- **Text Muted (`--text-muted`)**: `#A0A0A0`
- **Elevated Surfaces / Solid Buttons**: `#0A0A0A` con hover `#202020` y texto blanco.

### 2.3 Modo Oscuro (Deep Obsidian)
- **Background Page (`--bg-page`)**: `#0E0E12` (Negro carbón profundo)
- **Surface / Card (`--bg-card`)**: `#17171B` (Gris carbón oscuro elevado)
- **Card Border (`--border-card`)**: `#272730` (Línea sutil de separación)
- **Text Primary (`--text-primary`)**: `#F4F4F6` (Blanco perlado)
- **Text Secondary (`--text-secondary`)**: `#A0A0AB` (Gris medio suave)
- **Text Muted (`--text-muted`)**: `#787885` (Gris apagado)
- **Elevated Surfaces / Solid Buttons**: `#262630` con hover `#31313E` (Carbón elevado que evita empastar contra el fondo).

### 2.3b Tokens de Tailwind (uso en el código)

Los valores de §2.2 y §2.3 viven como variables CSS en `frontend/src/index.css` y se exponen como utilidades de Tailwind que **cambian solas con el tema** (no hace falta repetir `dark:`):

| Utilidad | Variable | Uso |
|---|---|---|
| `bg-page` | `--bg-page` | Fondo de página, inputs sobre tarjeta |
| `bg-card` | `--bg-card` | Superficie de tarjeta / modal |
| `border-line` / `border-line-strong` | `--border-card` / `--border-strong` | Bordes y separadores / hover de borde |
| `text-ink` / `text-ink-soft` / `text-ink-muted` | `--text-primary` / `--text-secondary` / `--text-muted` | Texto principal, secundario, apagado |
| `bg-brand` / `text-brand` / `bg-brand-hover` | `--color-primary` | Acento naranja `#FF5520` |
| `bg-solid` / `bg-solid-hover` | `--solid` / `--solid-hover` | Botones sólidos y navegación activa (`#0A0A0A` en claro, `#262630` en oscuro) |

No escribir hex ni pares `bg-white dark:bg-[#17171B]` en componentes nuevos. Las pantallas de acceso (`/login`, `/register`, `/verify-email`, onboarding) son siempre oscuras por diseño y conservan sus colores fijos. Componentes base reutilizables: `frontend/src/components/ui/` (`Button`, `Badge`, `Card`, `Modal`, `Input`/`Select`, `EmptyState`, `PageHeader`).

### 2.4 Estados y Semántica Funcional
- **Éxito (Validado / En Stock / Activo)**:
  - Claro: `text-emerald-600 bg-emerald-50 border-emerald-200`
  - Oscuro: `text-emerald-400 bg-emerald-500/10 border-emerald-500/20`
- **Alerta / Pendiente (Stock Bajo / DIAN Pendiente)**:
  - Claro: `text-amber-700 bg-amber-50 border-amber-200`
  - Oscuro: `text-amber-400 bg-amber-500/20 border-amber-500/20`
- **Error / Peligro / Agotado**:
  - Claro: `text-red-600 bg-red-50 border-red-200`
  - Oscuro: `text-red-400 bg-red-500/20 border-red-500/20`
- **Informativo / Proveedores / Factus**:
  - Claro: `text-blue-700 bg-blue-50 border-blue-200`
  - Oscuro: `text-blue-400 bg-blue-500/20 border-blue-500/20`

---

## 3. Tipografía y Números

- **Familia Tipográfica**: `Plus Jakarta Sans`, `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif`
- **Escala de Texto**:
  - **Display / Titulares de Bienvenida**: `2.25rem` / `36px` a `2.5rem` / `40px` (`font-light` combinado con `font-black text-[#FF5520]`)
  - **Títulos de Módulo / Sección**: `1.5rem` / `24px` a `1.75rem` / `28px` (`font-black` / `font-bold`)
  - **Encabezados Bento Card**: `1.125rem` / `18px` (`font-bold`)
  - **Texto Base / Labels**: `0.875rem` / `14px` (`font-medium` o `font-normal`)
  - **Subtítulos y Metadatos**: `0.75rem` / `12px` a `0.6875rem` / `11px` (`font-semibold` o `font-medium`, `uppercase tracking-wider`)
- **Numéricos y Moneda**:
  - Clase `.tabular-nums`: dígitos alineados verticalmente para precios, subtotales, totales de caja, códigos de barra y conteos de inventario. Formato COP (`$ 120.000`).

---

## 4. Geometría, Sombras y Micro-animaciones

- **Border Radius**:
  - Tarjetas Bento: `24px` (`rounded-3xl` / `rounded-[24px]`)
  - Botones, Badges e Inputs: `12px` (`rounded-xl`) a `9999px` (`rounded-full`)
- **Sombras de Elevación**:
  - Modo Claro: `0 2px 12px rgba(0,0,0,0.02)`
  - Modo Oscuro: `0 4px 24px rgba(0,0,0,0.4)`
  - Hover Elevado: `translateY(-2px)` con `box-shadow: 0 8px 24px rgba(0,0,0,0.05)` (claro) y `0 8px 32px rgba(0,0,0,0.6)` (oscuro).
- **Scrollbars Bento**:
  - Anchura ultra delgada: `6px`.
  - Pista invisible/neutra y thumb redondeado en gris suave (`#E2E2DF` claro / `#2D2D36` oscuro) con hover reactivo a `#FF5520`.

---

## 5. Arquitectura de Layout y Navegación

### 5.1 Sidebar Bento (Colapsable y Responsivo)
- **Header del Sidebar**: Logotipo con isotipo cúbico BentoIcon en `#FF5520`, tipografía `PosBento`, badge de status del establecimiento (`ACTIVE`).
- **Navegación Agrupada**:
  - **Principal**: Dashboard
  - **Operación**: Punto de Venta (POS), Productos, Inventario (con badge de items en stock bajo)
  - **Clientes y Facturación**: Clientes, Facturación DIAN, Documentos
  - **Administración**: Reportes, Configuración
- **Footer del Sidebar**:
  - Switcher de tema Día/Noche con icono animado (`Sun` / `Moon`).
  - Drawer de perfil de usuario (iniciales en avatar circular, rol en español: Administrador, Supervisor, Vendedor).
  - Botón de colapso rápido (`ChevronLeft` / `ChevronRight`).

### 5.2 Top Header
- Título contextual y subtítulo descriptivo según la ruta activa.
- Selector de fecha formateada en vivo (ej. `29 Sep, 2026`).
- Notificaciones en campana flotante con conteo no leído en tiempo real (alertas de stock bajo, aprobación/rechazo de documentos).
- Botón rápido "Punto de Venta" para saltar a facturar inmediatamente.

---

## 6. Módulos y Pantallas Principales

### 6.1 Punto de Venta (POS Terminal — `POSBento.jsx`)
- **Buscador & Scanner**: Input de código de barras / SKU con feedback auditivo (Web Audio API sintetizado) y toasts no bloqueantes para escaneo ultrarrápido sin interrupción modal.
- **Selector de Categorías**: Chips redondeados tipo píldora (`Todos`, `Bebidas`, `Snacks`, etc.).
- **Grid de Productos**: Tarjetas con imagen o fallback ilustrado, precio tabular destacado en negrita, badge de stock restante y botón de adición directa `+`.
- **Ticket / Carrito Lateral Bento**:
  - Desglose en tiempo real: items, cantidades con stepper interactivo `+/-`, notas o descuentos por ítem.
  - Subtotal, IVA desglosado, Total en gran formato.
  - Checkbox para emisión de **Factura Electrónica DIAN** vinculada a cliente NIT/Cédula.
  - Modal de cobro rápido con cálculo automático de cambio (Efectivo, Tarjeta, Transferencia, Pago Mixto).

### 6.2 Tablero de Control (Dashboard — `DashboardBento.jsx`)
- **Métricas KPI Bento**:
  - Ventas del día (Total acumulado con comparación porcentual).
  - Ticket promedio por cliente.
  - Facturas electrónicas generadas.
  - Alerta de productos con stock crítico.
- **Gráfico Semanal de Rendimiento**: Barras dinámicas Lun-Dom calculadas con native SQL / endpoints optimizados, resaltando el día de mayor facturación.
- **Ranking de Productos Top**: Lista bento con miniaturas, unidades vendidas e ingresos generados.

### 6.3 Facturación Electrónica DIAN (`InvoicingBento.jsx`)
- **Integración con Proveedor Tecnológico (Factus)**:
  - Monitor de habilitación del local (Sin Solicitar, Documentos Enviados, Esperando a Factus, Activa, Suspendida).
  - Gestión de rangos de numeración autorizados por la DIAN (Prefijo, Número de Resolución, Vigencia Desde/Hasta, Contador Consecutivo, Rango por defecto).
- **Listado de Facturas Electrónicas**:
  - Tabla bento con ID de venta, Cliente, Total, Fecha, y badge de estado: `PENDIENTE`, `VALIDADA`, `RECHAZADA`, `ERROR`.
  - Acciones directas: Ver CUFE y Código QR, Reintentar envío, Descargar XML oficial firmado y PDF representación gráfica, Enviar por email al adquirente.

### 6.4 Catálogo de Productos (`ProductsBento.jsx`) & Inventario (`InventoryBento.jsx`)
- **Gestión Integral**: SKU, código de barras con renderizado JsBarcode en cliente e impresión aislada por CSS `@media print`.
- **Precios e Impuestos**: Precio de compra, precio de venta, margen de ganancia calculado y tarifa de IVA (0%, 5%, 19%).
- **Kardex y Movimientos**: Entradas de compras, ajustes manuales, mermas y auditoría de stock.

### 6.5 Backoffice Suite (`backoffice/`)
- Módulo para `SUPER_ADMIN` con panel de control de múltiples tiendas (onboarding, validación KYC de RUT y Cámara de Comercio, asignación de credenciales cifradas AES-256-GCM para Factus por tienda, catálogo global de categorías).

---

## 7. Tokens para Configuración en Stitch MCP

```json
{
  "displayName": "PosBento Design System",
  "theme": {
    "headlineFont": "PLUS_JAKARTA_SANS",
    "bodyFont": "PLUS_JAKARTA_SANS",
    "labelFont": "PLUS_JAKARTA_SANS",
    "colorMode": "LIGHT",
    "customColor": "#FF5520",
    "overridePrimaryColor": "#FF5520",
    "overrideNeutralColor": "#F7F7F5",
    "roundness": "ROUND_TWELVE",
    "colorVariant": "VIBRANT"
  }
}
```
