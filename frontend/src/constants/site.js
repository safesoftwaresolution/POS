// Pagina publica del producto (landing). Se puede sobreescribir al compilar con VITE_LANDING_URL.
export const LANDING_URL = (import.meta.env.VITE_LANDING_URL ?? 'https://posbento.com').replace(/\/+$/, '');
