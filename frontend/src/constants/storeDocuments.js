// Tipos de documento KYC de un local — deben coincidir con el CHECK de BD
// (CK_store_docs_type en V3__Multitenancy_And_Backoffice.sql) y con la validación
// de StoreServiceImpl#uploadOwnDocument. Compartido entre la vista self-service del
// local (pages/DocumentsBento.jsx) y la revisión del backoffice (pages/backoffice/StoresBento.jsx).

export const DOCUMENT_TYPES = ['RUT', 'COMMERCE_CHAMBER', 'ID_CARD', 'BANK_CERTIFICATE', 'OTHER'];

export const DOC_TYPE_LABELS = {
  RUT: 'RUT',
  COMMERCE_CHAMBER: 'Cámara de Comercio',
  ID_CARD: 'Cédula',
  BANK_CERTIFICATE: 'Certificación Bancaria',
  OTHER: 'Otro',
};

export const DOC_STATUS_LABELS = {
  PENDING: 'En revisión',
  APPROVED: 'Aprobado',
  REJECTED: 'Rechazado',
};

export const DOC_STATUS_STYLES = {
  PENDING: 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-400',
  APPROVED: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20',
  REJECTED: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400',
};
