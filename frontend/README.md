# Frontend — POS

SPA en **React 19 + Vite + Tailwind CSS 4** (interfaz "Bento", tema claro/oscuro). Es un consumidor puro de la API del backend: no contiene lógica de negocio.

## Comandos

Requiere el backend corriendo en `http://localhost:8080` (Vite hace proxy de `/api`).

```bash
npm install
npm run dev       # Servidor de desarrollo en http://localhost:3000
npm run build     # Build de producción
npm run preview   # Previsualizar el build
npm run lint      # oxlint
```

## Estructura

- `src/pages/` — páginas del local; `backoffice/` (rol `SUPER_ADMIN`) y `onboarding/`.
- `src/components/` — layout (Header/Sidebar del local y del back office), POS, clientes, auth.
- `src/services/` — un cliente por módulo; `http.js` es el cliente compartido (JWT, refresh transparente ante 401).
- `src/context/` — `AuthContext`, `ThemeContext`, `ModalContext`.

Rutas, roles y módulos del backend: ver el [README principal](../README.md) y [`backend/docs/`](../backend/docs/_index.md).
