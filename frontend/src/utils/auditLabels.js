// Textos del historial de auditoría de clientes.

export const AUDIT_ACTION_LABELS = {
  CREATE: 'Creado',
  UPDATE: 'Editado',
  DELETE: 'Eliminado',
};

export const CUSTOMER_FIELD_LABELS = {
  fullName: 'Nombre',
  identification: 'Identificación',
  email: 'Correo',
  phone: 'Teléfono',
  address: 'Dirección',
  dv: 'Dígito de verificación',
  legalOrganizationId: 'Tipo de persona',
  tributeId: 'Responsabilidad tributaria',
  municipalityId: 'Municipio',
  active: 'Activo',
};

/** Valor legible de un campo auditado: vacío como "—" y booleanos en español. */
export function formatAuditValue(value) {
  if (value === null || value === undefined || value === '') return '—';
  if (value === true) return 'Sí';
  if (value === false) return 'No';
  return String(value);
}

/** Etiqueta de un campo; si no se conoce, el nombre técnico. */
export function fieldLabel(field, labels = CUSTOMER_FIELD_LABELS) {
  return labels[field] || field;
}
