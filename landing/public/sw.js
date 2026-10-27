// posbento.com sirvio antes el sistema (con service worker); ahora es la landing, que no usa ninguno.
// Este archivo reemplaza al viejo en los navegadores que lo conservan: se desregistra y deja la pagina limpia.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.map((k) => caches.delete(k))))
      .then(() => self.registration.unregister())
  );
});
