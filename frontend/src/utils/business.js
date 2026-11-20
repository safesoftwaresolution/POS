// Etiquetas y ayudas de la pantalla de negocio con varias sucursales.

export const BILLING_MODE_LABELS = {
  SEPARATE_TAX_ID: 'NIT distinto por sucursal',
  SHARED_TAX_ID: 'Mismo NIT en todas',
};

export const BILLING_MODE_HINTS = {
  SEPARATE_TAX_ID: 'Cada sucursal factura con su propia empresa y su propia numeración.',
  SHARED_TAX_ID: 'Todas las sucursales facturan con la empresa y la numeración de la sucursal principal.',
};

const BRANCH_STATUS_LABELS = {
  ACTIVE: 'Activa',
  PENDING_VERIFICATION: 'En revisión',
  REJECTED: 'Rechazada',
  SUSPENDED: 'Suspendida',
  INACTIVE: 'Inactiva',
};

const BRANCH_STATUS_TONES = {
  ACTIVE: 'success',
  PENDING_VERIFICATION: 'warning',
  REJECTED: 'danger',
  SUSPENDED: 'warning',
  INACTIVE: 'neutral',
};

export const branchStatusLabel = (status) => BRANCH_STATUS_LABELS[status] || status || '—';
export const branchStatusTone = (status) => BRANCH_STATUS_TONES[status] || 'neutral';

/** ¿Puede crear otra sucursal? El tope del negocio incluye la sucursal principal. */
export const canAddBranch = (business) => Boolean(business) && business.branches.length < business.maxBranches;

const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const pad = (n) => String(n).padStart(2, '0');
const isoOf = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

/**
 * Serie diaria para la gráfica: un punto por cada día de `from` a `to` (inclusive), con 0 en los días sin ventas
 * (el backend solo devuelve los días que vendieron). `daily` es `[{ date: 'yyyy-MM-dd', amount }]`.
 */
export function fillDailySeries(daily, from, to) {
  if (!from || !to) return [];
  const byDate = new Map((daily || []).map((d) => [d.date, Number(d.amount) || 0]));
  const [fy, fm, fd] = from.split('-').map(Number);
  const cursor = new Date(fy, fm - 1, fd);
  const series = [];
  // Tope defensivo por si llegan fechas inválidas: el backend no acepta más de 366 días.
  for (let i = 0; i < 400; i += 1) {
    const iso = isoOf(cursor);
    if (iso > to) break;
    series.push({
      key: iso,
      label: String(cursor.getDate()),
      detail: `${cursor.getDate()} ${MONTHS[cursor.getMonth()]} ${cursor.getFullYear()}`,
      value: byDate.get(iso) ?? 0,
    });
    cursor.setDate(cursor.getDate() + 1);
  }
  return series;
}
