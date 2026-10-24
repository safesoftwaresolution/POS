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
