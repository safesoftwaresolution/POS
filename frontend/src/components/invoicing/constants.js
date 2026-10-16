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
  DOCUMENTS_SUBMITTED: 'Documentos enviados',
  AWAITING_PROVIDER: 'Esperando a Factus',
  ACTIVE: 'Activa',
  REJECTED: 'Rechazada',
  SUSPENDED: 'Suspendida'
};

export const ACTIVATION_STYLES = {
  NOT_REQUESTED: 'bg-gray-100 dark:bg-white/10 text-gray-600 dark:text-gray-300',
  DOCUMENTS_SUBMITTED: 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400 border border-amber-500/20',
  AWAITING_PROVIDER: 'bg-blue-100 dark:bg-blue-500/20 text-blue-700 dark:text-blue-400 border border-blue-500/20',
  ACTIVE: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20',
  REJECTED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20',
  SUSPENDED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400 border border-red-500/20'
};

export const EMPTY_RANGE_FORM = { document: '', prefix: '', resolutionNumber: '', current: '' };

export const FIELD_CLASS =
  'w-full p-2.5 bg-white dark:bg-[#0E0E12] border border-line rounded-xl text-xs font-semibold text-ink focus:outline-none focus:border-ink dark:focus:border-brand';

export const formatDateTime = (value) => (value ? new Date(value).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' }) : '—');
