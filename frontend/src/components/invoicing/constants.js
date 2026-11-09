export const INVOICE_STATUSES = ['VALIDATED', 'PENDING', 'ERROR', 'REJECTED'];

export const INVOICE_STATUS_LABELS = {
  PENDING: 'Pendiente',
  VALIDATED: 'Validada',
  REJECTED: 'Rechazada',
  ERROR: 'Con error'
};

export const INVOICE_STATUS_STYLES = {
  PENDING: 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400 border border-amber-500/20',
  VALIDATED: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20',
  REJECTED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20',
  ERROR: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20'
};

export const ACTIVATION_LABELS = {
  NOT_REQUESTED: 'Sin solicitar',
  IN_SETUP: 'En configuración',
  ACTIVE: 'Activa',
  SUSPENDED: 'Suspendida'
};

export const ACTIVATION_STYLES = {
  NOT_REQUESTED: 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300',
  IN_SETUP: 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400 border border-amber-500/20',
  ACTIVE: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20',
  SUSPENDED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20'
};

// Documentos de un rango de numeración (códigos DIAN). Solo las facturas (01) llevan resolución.
export const INVOICE_DOCUMENT = '01';

export const DOCUMENT_OPTIONS = [
  { value: '01', label: 'Factura de venta' },
  { value: '91', label: 'Nota crédito' },
  { value: '92', label: 'Nota débito' },
];

export const isInvoiceDocument = (document) => document === INVOICE_DOCUMENT;

export const FIELD_CLASS =
  'w-full p-2.5 bg-white dark:bg-[#0E0E12] border border-line rounded-xl text-xs font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand';

export const formatDateTime = (value) => (value ? new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' }) : '—');
