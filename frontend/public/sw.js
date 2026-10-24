// Service worker mínimo: el navegador lo exige para ofrecer "Instalar aplicación".
// No cachea nada a propósito: la app es multi-tenant con sesión, stock y ventas en vivo,
// así que todo va directo a la red.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()));
self.addEventListener('fetch', () => {});
