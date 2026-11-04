/** Ventana por defecto (días) para considerar un producto "por vencer". */
export const EXPIRY_WINDOW_DAYS = 30;

/**
 * `YYYY-MM-DD` → `Date` en hora local (medianoche). `new Date('2026-11-15')` se interpreta como UTC y en
 * Colombia (UTC-5) cae en el día anterior, así que no se usa.
 */
export function parseLocalDate(iso) {
  if (!iso) return null;
  const [y, m, d] = String(iso).slice(0, 10).split('-').map(Number);
  if (!y || !m || !d) return null;
  return new Date(y, m - 1, d);
}

/** Días que faltan para la fecha (negativo si ya pasó), o `null` si no hay fecha. */
export function daysUntil(iso, today = new Date()) {
  const date = parseLocalDate(iso);
  if (!date) return null;
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((date - start) / 86400000);
}

/** `none` (sin fecha) · `expired` · `today` · `soon` (dentro de la ventana) · `ok` (más lejos). */
export function expiryStatus(iso, windowDays = EXPIRY_WINDOW_DAYS) {
  const days = daysUntil(iso);
  if (days === null) return 'none';
  if (days < 0) return 'expired';
  if (days === 0) return 'today';
  return days <= windowDays ? 'soon' : 'ok';
}

/** Texto corto de cuánto falta: "Vence en 12 días", "Vence hoy", "Vencido hace 3 días". */
export function expiryLabel(days) {
  if (days === null || days === undefined) return '';
  if (days < -1) return `Vencido hace ${-days} días`;
  if (days === -1) return 'Venció ayer';
  if (days === 0) return 'Vence hoy';
  if (days === 1) return 'Vence mañana';
  return `Vence en ${days} días`;
}

/** "15 nov 2026" a partir de `YYYY-MM-DD`. */
export function formatExpiryDate(iso) {
  const date = parseLocalDate(iso);
  return date ? date.toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';
}
