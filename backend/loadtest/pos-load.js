// Prueba de carga del POS (k6). Ver README.md en esta carpeta.
//
//   k6 run -e BASE_URL=https://api.staging -e PROFILE=100 -e USERS='tienda1:Pass1,tienda2:Pass2' pos-load.js
//
// Cada "local" simulado es un VU que repite: buscar producto -> crear venta, con ~0,5 req/s
// (una iteracion cada ~2 s, de ahi el sleep). Un escenario aparte lanza reportes pesados en paralelo
// para medir cuanto afectan al p95 de POST /api/v1/sales.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PROFILE = Number(__ENV.PROFILE || 50);              // locales simulados: 50 | 100 | 200
const REPORT_VUS = Number(__ENV.REPORT_VUS || 10);        // reportes concurrentes (0 = sin escenario de reportes)
const DURATION = __ENV.DURATION || '10m';
const REPORT_DAYS = Number(__ENV.REPORT_DAYS || 30);

// "usuario:clave,usuario:clave". Con una sola cuenta todos los VUs comparten tienda (sirve para humo,
// no para medir aislamiento): para 100 locales reales usa tiendas de prueba distintas.
const USERS = (__ENV.USERS || 'admin:Password123').split(',').map((u) => {
  const [username, ...rest] = u.split(':');
  return { username, password: rest.join(':') };
});

const saleDuration = new Trend('sale_duration', true);
const reportDuration = new Trend('report_duration', true);
const errors = new Rate('business_errors');

export const options = {
  scenarios: {
    stores: {
      executor: 'ramping-vus',
      exec: 'storeFlow',
      startVUs: 0,
      stages: [
        { duration: '2m', target: PROFILE },
        { duration: DURATION, target: PROFILE },
        { duration: '1m', target: 0 },
      ],
    },
    ...(REPORT_VUS > 0 && {
      reports: {
        executor: 'constant-vus',
        exec: 'reportFlow',
        vus: REPORT_VUS,
        startTime: '3m',          // cuando las tiendas ya estan en regimen
        duration: DURATION,
      },
    }),
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'sale_duration': ['p(95)<800'],   // umbral de la Fase 6
    'report_duration': ['p(95)<5000'],
    'business_errors': ['rate<0.01'],
  },
};

function login(user) {
  const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify(user), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'login' },
  });
  if (!check(res, { 'login 200': (r) => r.status === 200 })) return null;
  return { Authorization: `Bearer ${res.json('token')}`, 'Content-Type': 'application/json' };
}

// Una sesion por VU (los tokens duran 15 min por defecto): se renueva si expira.
let session = null;
let sessionAt = 0;
function auth() {
  const user = USERS[(__VU - 1) % USERS.length];
  if (!session || Date.now() - sessionAt > 10 * 60 * 1000) {
    session = login(user);
    sessionAt = Date.now();
  }
  return session;
}

export function storeFlow() {
  const headers = auth();
  if (!headers) { errors.add(1); sleep(5); return; }

  const search = http.get(`${BASE_URL}/api/v1/products?active=true&size=10`, { headers, tags: { name: 'products_search' } });
  const ok = check(search, { 'productos 200': (r) => r.status === 200 });
  const products = ok ? (search.json('content') || []) : [];
  if (products.length === 0) { errors.add(1); sleep(2); return; }

  const p = products[Math.floor(Math.random() * products.length)];
  const body = JSON.stringify({
    cashReceived: 1000000,
    sendToFactus: false,          // la facturacion electronica es asincrona y externa: no se mide aqui
    paymentMethod: 'CASH',
    items: [{ productId: p.id, quantity: 1 }],
  });
  const sale = http.post(`${BASE_URL}/api/v1/sales`, body, { headers, tags: { name: 'sales_create' } });
  saleDuration.add(sale.timings.duration);
  // 4xx de negocio (p. ej. sin stock) se cuentan aparte: no son saturacion del servidor.
  errors.add(!check(sale, { 'venta 2xx': (r) => r.status >= 200 && r.status < 300 }));

  sleep(1.5 + Math.random());      // ~0,5 req/s por local
}

export function reportFlow() {
  const headers = auth();
  if (!headers) { sleep(5); return; }
  const to = new Date();
  const from = new Date(to.getTime() - REPORT_DAYS * 24 * 3600 * 1000);
  const q = `dateFrom=${from.toISOString().slice(0, 19)}&dateTo=${to.toISOString().slice(0, 19)}`;
  for (const path of ['sales', 'sales-heatmap', 'top-products', 'profitability']) {
    const res = http.get(`${BASE_URL}/api/v1/reports/${path}?${q}`, { headers, tags: { name: `report_${path}` } });
    reportDuration.add(res.timings.duration);
    check(res, { [`reporte ${path} 200`]: (r) => r.status === 200 });
  }
  sleep(1);
}
