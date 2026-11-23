/** Categorías sugeridas por tipo de movimiento; el campo es libre (el backend acepta cualquier texto). */
export const MOVEMENT_CATEGORIES = {
  EXPENSE: ['Arriendo', 'Servicios', 'Nómina', 'Transporte', 'Publicidad', 'Impuestos', 'Mantenimiento', 'Otros'],
  INCOME: ['Otros ingresos', 'Venta por fuera del POS', 'Préstamo', 'Aporte del dueño'],
};

export const MOVEMENT_TYPES = [
  { id: 'EXPENSE', label: 'Egreso (sale dinero)' },
  { id: 'INCOME', label: 'Ingreso (entra dinero)' },
];

/** Variación porcentual lista para mostrar: `+12,5 %`, `-3,0 %`, `0,0 %`, con su dirección. */
export function formatVariation(pct) {
  const value = Number(pct) || 0;
  const direction = value > 0 ? 'up' : value < 0 ? 'down' : 'flat';
  const text = `${value > 0 ? '+' : ''}${value.toLocaleString('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} %`;
  return { text, direction };
}

/** Cuerpo de `POST/PUT /finance/movements` a partir del formulario (el monto llega como dígitos). */
export function movementPayload(form) {
  return {
    type: form.type,
    category: form.category.trim(),
    description: form.description.trim() || null,
    amount: Number(form.amount),
    paymentMethod: form.paymentMethod || 'CASH',
    movementDate: form.movementDate || null,
  };
}

/** Mensaje de lo que falta en el formulario de un movimiento, o '' si está completo. */
export function movementFormError(form, today) {
  if (!form.type) return 'Indica si es un egreso o un ingreso.';
  if (!form.category.trim()) return 'Elige o escribe una categoría.';
  if (!(Number(form.amount) > 0)) return 'El monto debe ser mayor a 0.';
  if (form.movementDate && today && form.movementDate > today) return 'La fecha no puede ser futura.';
  return '';
}

/** Cuerpo de `POST/PUT /finance/fixed-costs`. El día de cobro es opcional (vacío = sin día fijo). */
export function fixedCostPayload(form) {
  return {
    name: form.name.trim(),
    monthlyAmount: Number(form.monthlyAmount),
    dueDay: form.dueDay === '' || form.dueDay === null ? null : Number(form.dueDay),
    active: form.active,
  };
}

export function fixedCostFormError(form) {
  if (!form.name.trim()) return 'El nombre es obligatorio.';
  if (!(Number(form.monthlyAmount) > 0)) return 'El monto mensual debe ser mayor a 0.';
  if (form.dueDay !== '' && form.dueDay !== null) {
    const day = Number(form.dueDay);
    if (!Number.isInteger(day) || day < 1 || day > 31) return 'El día de cobro debe estar entre 1 y 31.';
  }
  return '';
}

const pad = (n) => String(n).padStart(2, '0');
const daysInMonth = (year, monthIndex) => new Date(year, monthIndex + 1, 0).getDate();

/**
 * Próxima fecha de cobro de un costo con `dueDay` (1-31), contando hoy. En un mes más corto vale su último día
 * (día 31 → 28 o 30). Devuelve `{ date: 'yyyy-MM-dd', daysLeft }`, o null si no tiene día fijo.
 */
export function nextDueDate(dueDay, today = new Date()) {
  if (!dueDay) return null;
  const base = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const at = (year, monthIndex) => new Date(year, monthIndex, Math.min(dueDay, daysInMonth(year, monthIndex)));
  let due = at(base.getFullYear(), base.getMonth());
  if (due < base) due = at(base.getFullYear(), base.getMonth() + 1);
  const daysLeft = Math.round((due - base) / 86400000);
  return { date: `${due.getFullYear()}-${pad(due.getMonth() + 1)}-${pad(due.getDate())}`, daysLeft };
}

/** Texto del vencimiento: «Hoy», «Mañana» o «En N días». */
export function dueLabel(daysLeft) {
  if (daysLeft === 0) return 'Hoy';
  if (daysLeft === 1) return 'Mañana';
  return `En ${daysLeft} días`;
}
