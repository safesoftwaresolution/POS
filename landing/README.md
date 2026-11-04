# PosBento — Landing

Página pública de `posbento.com`. Es un sitio **estático** (Astro + Tailwind CSS 4) sin lógica de negocio: presenta el producto y lleva al sistema (`app.posbento.com`) con los botones **Ingresar** y **Crear cuenta**.

Es independiente de `frontend/` y `backend/`: no comparte código ni dependencias con el POS.

## Comandos

Requiere Node 22+ (`.nvmrc`). Desde `landing/`:

```bash
npm install
npm run dev       # http://localhost:4321
npm run build     # genera dist/
npm run preview   # sirve dist/ en :4321
```

## Variable de entorno

| Variable | Descripción | Por defecto |
|---|---|---|
| `PUBLIC_APP_URL` | Origen del sistema al que apuntan los botones, los enlaces del pie y las redirecciones | `https://app.posbento.com` |

> Se **incrusta al compilar**: si la cambias hay que volver a construir (en Dokploy, *Deploy* de nuevo). Mientras el sistema siga en `https://posbento.com` (antes del corte de dominios), define `PUBLIC_APP_URL=https://posbento.com` en la prueba de Dokploy para que los botones lleven al sistema actual. Ver `.env.example`.

## Estructura

```
landing/
├── public/                 favicon, íconos, og.png (imagen para redes), robots.txt
├── scripts/og.html         plantilla de public/og.png (1200×630)
└── src/
    ├── layouts/Base.astro  <head> (SEO, Open Graph, tema) + scripts de tema, menú y animaciones
    ├── components/         Hero, Features, Dian, Steps, Roles, Faq, FinalCta, Header, Footer…
    ├── lib/                site.js (URLs), faq.js (preguntas), icons.js (lucide)
    ├── pages/
    │   ├── index.astro     la landing
    │   ├── 404.astro
    │   └── login, register, verify-email, forgot-password, reset-password   (solo redirigen al sistema)
    └── styles/global.css   tokens de diseño (los mismos nombres que frontend/src/index.css) y componentes
```

- **Diseño:** hereda `DESIGN.md` (tarjetas bento de 24 px, naranja `#FF5520`, Plus Jakarta Sans autoalojada, tema claro/oscuro). Texto sobre naranja en negro: blanco sobre `#FF5520` no llega a contraste AA.
- **Contenido:** solo afirma lo que el sistema hace hoy. No hay precios, testimonios ni cifras reales; las cifras de las maquetas son ficticias y así se indica en la página.
- **Redirecciones:** antes de que la landing ocupara `posbento.com`, el sistema vivía ahí. Los correos ya enviados (`/verify-email?token=…`, `/reset-password?token=…`) y los favoritos viejos llegan a esas rutas; las páginas de `pages/` los reenvían a `PUBLIC_APP_URL` conservando la ruta, el `?token=` y el `#hash`.
- **Imagen para redes:** `public/og.png` se genera abriendo `scripts/og.html` a 1200×630 y capturando la página.

## Despliegue en Dokploy

Aplicación nueva, **separada** de las del frontend y el backend:

| Campo | Valor |
|---|---|
| Provider | GitHub, mismo repositorio y rama que el resto |
| **Build Path** | `/landing` |
| **Build Type** | Nixpacks |
| **Publish Directory** | `dist` |
| Environment | `PUBLIC_APP_URL=…` (ver arriba) |

Nixpacks detecta Node por `package.json` (`engines.node >= 22`), ejecuta `npm ci` + `npm run build` y Dokploy sirve `dist/` con NGINX.

1. **Prueba (Fase 2):** asigna un dominio temporal (*Domains*) con HTTPS y revisa la página, los botones y las redirecciones (`/verify-email?token=x`).
2. **Corte (más adelante):** cuando `app.posbento.com` ya sirva el sistema, quita `posbento.com` y `www` de la app del frontend, agrégalos a esta app y define `PUBLIC_APP_URL=https://app.posbento.com` (o borra la variable) y vuelve a desplegar.
