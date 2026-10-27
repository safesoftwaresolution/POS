// Origen publico del sistema (POS). Se incrusta al compilar (PUBLIC_APP_URL); por defecto es el subdominio final.
export const APP_URL = (import.meta.env.PUBLIC_APP_URL ?? 'https://app.posbento.com').replace(/\/+$/, '');
export const SITE_URL = 'https://posbento.com';
export const SITE_NAME = 'PosBento';

export const links = {
  login: `${APP_URL}/login`,
  register: `${APP_URL}/register`,
  forgot: `${APP_URL}/forgot-password`,
  terms: `${APP_URL}/legal/terms-and-conditions`,
  privacy: `${APP_URL}/legal/privacy-policy`,
  support: `${APP_URL}/support`,
};

export const nav = [
  { href: '/#funciones', label: 'Funciones' },
  { href: '/#facturacion', label: 'Facturación DIAN' },
  { href: '/#como-funciona', label: 'Cómo funciona' },
  { href: '/#preguntas', label: 'Preguntas' },
];
