// Motivos, etiquetas y reglas del modal "Mover stock" y de la pestaña Movimientos.
// Sin React: qué se le manda al backend (tipo, cantidad, motivo) se decide aquí, en funciones puras.

// Las grafías son las que ya escriben la importación y el formulario de producto (ENTRY, SALIDA, AJUSTE);
// DEVOLUCION es la devolución al proveedor (no confundir con DEVOLUCION_VENTA, la de un cliente).
// AJUSTE en el backend solo suma, así que un ajuste hacia abajo viaja como SALIDA.

export const MAX_QUANTITY = 1000000;
// Largo de la columna inventory_movements.reason; el backend no lo valida, así que el límite es del cliente.
export const REASON_MAX_LENGTH = 255;

export const OPERATIONS = [
  { id: 'PURCHASE', label: 'Compra a proveedor' },
  { id: 'ENTRY', label: 'Entrada' },
  { id: 'EXIT', label: 'Salida' },
  { id: 'COUNT', label: 'Contar y ajustar' },
];

export const ENTRY_REASONS = [
  { code: 'initial', label: 'Inventario inicial' },
  { code: 'existing', label: 'Mercancía que ya tenía' },
  { code: 'bonus', label: 'Bonificación u obsequio del proveedor' },
  { code: 'other', label: 'Otro', requiresNote: true },
];

export const EXIT_REASONS = [
  { code: 'expired', label: 'Vencimiento' },
  { code: 'damaged', label: 'Daño o rotura' },
  { code: 'lost', label: 'Pérdida o robo' },
  { code: 'own_use', label: 'Consumo propio' },
  { code: 'sample', label: 'Muestra u obsequio' },
  { code: 'supplier_return', label: 'Devolución a proveedor', movementType: 'DEVOLUCION' },
  { code: 'other', label: 'Otro', requiresNote: true },
];

export function reasonsFor(operation) {
  if (operation === 'ENTRY') return ENTRY_REASONS;
  if (operation === 'EXIT') return EXIT_REASONS;
  return null;
}

// El kardex mezcla alias (ENTRY/ENTRADA, SALIDA/EXIT, ...): todos se muestran con la misma etiqueta.
const ENTRY_INFO = { label: 'Entrada', tone: 'success' };
const EXIT_INFO = { label: 'Salida', tone: 'danger' };
const ADJUST_INFO = { label: 'Ajuste', tone: 'info' };
const SUPPLIER_RETURN_INFO = { label: 'Devolución a proveedor', tone: 'warning' };

const TYPE_INFO = {
  ENTRY: ENTRY_INFO,
  ENTRADA: ENTRY_INFO,
  COMPRA: { label: 'Compra', tone: 'brand' },
  AJUSTE: ADJUST_INFO,
  ADJUSTMENT: ADJUST_INFO,
  DEVOLUCION_VENTA: { label: 'Devolución de cliente', tone: 'warning' },
  SALIDA: EXIT_INFO,
  EXIT: EXIT_INFO,
  VENTA: { label: 'Venta', tone: 'neutral' },
  DEVOLUCION: SUPPLIER_RETURN_INFO,
  RETURN: SUPPLIER_RETURN_INFO,
};

/** Etiqueta y tono de un `movementType` del backend; un tipo desconocido se muestra tal cual. */
export function movementTypeInfo(type) {
  return TYPE_INFO[String(type || '').toUpperCase().trim()] || { label: type || '—', tone: 'neutral' };
}

const fmt = (n) => n.toLocaleString('es-CO');

function parseWhole(text, min) {
  const t = String(text ?? '').trim();
  if (!/^\d+$/.test(t)) return null;
  const n = Number(t);
  return n >= min && n <= MAX_QUANTITY ? n : null;
}

/** Cantidad entera de 1 a MAX_QUANTITY, o null. */
export const parseQuantity = (text) => parseWhole(text, 1);
/** Conteo físico entero de 0 a MAX_QUANTITY, o null. */
export const parseCount = (text) => parseWhole(text, 0);

/** Texto del motivo: la etiqueta, o `etiqueta: nota` si se escribió una nota. Nunca pasa de REASON_MAX_LENGTH. */
export function buildReason(reason, note = '') {
  const n = note.trim();
  return (n ? `${reason.label}: ${n}` : reason.label).slice(0, REASON_MAX_LENGTH);
}

/** Largo máximo de la nota para que `etiqueta: nota` quepa en la columna. */
export const noteMaxLength = (reason) => REASON_MAX_LENGTH - reason.label.length - 2;

const fail = (error, extra = {}) => ({ ok: false, error, ...extra });

function planCount({ counted, note, currentStock }) {
  const target = parseCount(counted);
  if (target == null) return fail(`Ingresa la cantidad contada (un número entero de 0 a ${fmt(MAX_QUANTITY)}).`);
  const diff = target - currentStock;
  if (diff === 0) return fail('El conteo coincide con el sistema: no hay nada que ajustar.', { diff });
  const base = `Conteo físico: sistema ${currentStock}, contado ${target}`;
  const n = note.trim();
  return {
    ok: true,
    diff,
    // AJUSTE solo suma en el backend: un faltante viaja como SALIDA, igual que en el conteo por Excel.
    movementType: diff > 0 ? 'AJUSTE' : 'SALIDA',
    quantity: Math.abs(diff),
    reason: (n ? `${base}. ${n}` : base).slice(0, REASON_MAX_LENGTH),
  };
}

/** Valida la compra a proveedor (se envía a `POST /purchases`, no a /inventory/movements). */
export function planPurchase({ supplierId = '', quantity = '' }) {
  if (!supplierId) return fail('Selecciona un proveedor.');
  const qty = parseQuantity(quantity);
  if (qty == null) return fail(`Ingresa una cantidad entera entre 1 y ${fmt(MAX_QUANTITY)}.`);
  return { ok: true, quantity: qty };
}

/**
 * Convierte lo que escribió el usuario en el movimiento que se envía a `POST /inventory/movements`.
 * `operation`: 'ENTRY' | 'EXIT' | 'COUNT'. La compra a proveedor no pasa por aquí (usa /purchases).
 * Devuelve `{ ok: true, movementType, quantity, reason }` o `{ ok: false, error }`.
 */
export function planMovement({ operation, quantity = '', counted = '', reasonCode = '', note = '', currentStock = 0 }) {
  if (operation === 'COUNT') return planCount({ counted, note, currentStock });

  const reasons = reasonsFor(operation);
  if (!reasons) return fail('Elige una operación válida.');

  const qty = parseQuantity(quantity);
  if (qty == null) return fail(`Ingresa una cantidad entera entre 1 y ${fmt(MAX_QUANTITY)}.`);

  const reason = reasons.find((r) => r.code === reasonCode);
  if (!reason) return fail('Elige el motivo del movimiento.');
  if (reason.requiresNote && !note.trim()) return fail('Cuéntanos el motivo en la nota.');

  if (operation === 'EXIT' && qty > currentStock) {
    return fail(`No puedes sacar más de lo que hay (stock actual: ${fmt(currentStock)}).`);
  }

  return {
    ok: true,
    movementType: reason.movementType || (operation === 'ENTRY' ? 'ENTRY' : 'SALIDA'),
    quantity: qty,
    reason: buildReason(reason, note),
  };
}
