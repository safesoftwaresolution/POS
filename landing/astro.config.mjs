import { defineConfig } from 'astro/config';
import tailwindcss from '@tailwindcss/vite';
import sitemap from '@astrojs/sitemap';

// Rutas que solo reenvian al sistema (src/pages/login.astro, ...) y la 404: fuera del sitemap.
const NO_INDEX = /\/(login|register|verify-email|forgot-password|reset-password|404)\/?$/;

export default defineConfig({
  site: 'https://posbento.com',
  output: 'static',
  // /login/index.html: nginx (Dokploy "publish directory") lo sirve sin reglas extra.
  build: { format: 'directory' },
  devToolbar: { enabled: false },
  integrations: [sitemap({ filter: (page) => !NO_INDEX.test(page) })],
  vite: { plugins: [tailwindcss()] },
});
