/** Pesos colombianos sin decimales: 12500 → "$12.500". */
export const money = (n) => `$${Math.round(Number(n) || 0).toLocaleString('es-CO')}`;

/** Fecha local `YYYY-MM-DD` de hace `days` días (0 = hoy), sin el desfase de UTC de `toISOString`. */
export function isoDaysAgo(days = 0) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Rango de un día completo para los endpoints que reciben `LocalDateTime`. */
export const dayStart = (isoDate) => `${isoDate}T00:00:00`;
export const dayEnd = (isoDate) => `${isoDate}T23:59:59`;

/** Etiqueta legible de un código de método de pago (los desconocidos se muestran tal cual). */
const PAYMENT_LABELS = { CASH: 'Efectivo', CARD: 'Tarjeta', NEQUI: 'Nequi', TRANSFER: 'Transferencia' };
export const paymentLabel = (code) => PAYMENT_LABELS[code] || code || '—';

const copFormatter = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });

/** Deja solo los dígitos (pesos enteros). Un "12000.50" que llegue del backend pierde los decimales. */
export function toDigits(value) {
  if (value === null || value === undefined || value === '') return '';
  if (typeof value === 'number') return Number.isFinite(value) ? String(Math.round(value)) : '';
  return String(value).split('.')[0].replace(/\D/g, '');
}

/** Texto con separador de miles colombiano para un campo de dinero (10000 → "10.000"; vacío → ""). */
export function formatCop(value) {
  const digits = toDigits(value);
  return digits ? copFormatter.format(Number(digits)) : '';
}

/** Número con separador de miles de es-CO: 12500 → "12.500". */
export const formatNumber = (n) => Number(n || 0).toLocaleString('es-CO');

const toDate = (iso) => (iso ? new Date(iso) : null);

/** Fecha y hora compactas para listas: "05 oct, 03:20 p. m." (sin valor → "—"). */
export const formatDateTime = (iso) =>
  iso ? toDate(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—';

/** Fecha y hora con año, para auditorías y registros: "5 oct 2026, 3:20 p. m." (sin valor → "—"). */
export const formatDateTimeFull = (iso) =>
  iso ? toDate(iso).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' }) : '—';

/** Fecha larga sin hora: "5 de octubre de 2026". `fallback` es lo que se devuelve cuando no hay fecha. */
export const formatLongDate = (iso, fallback = '—') =>
  iso ? toDate(iso).toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric' }) : fallback;
