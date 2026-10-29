import { describe, expect, it } from 'vitest';
import { fieldLabel, formatAuditValue } from './auditLabels';

describe('auditLabels', () => {
  it('muestra vacío como guion y booleanos en español', () => {
    expect(formatAuditValue(null)).toBe('—');
    expect(formatAuditValue(undefined)).toBe('—');
    expect(formatAuditValue('')).toBe('—');
    expect(formatAuditValue(true)).toBe('Sí');
    expect(formatAuditValue(false)).toBe('No');
    expect(formatAuditValue(13)).toBe('13');
    expect(formatAuditValue('Maria')).toBe('Maria');
  });

  it('traduce los campos conocidos y deja los demás como están', () => {
    expect(fieldLabel('fullName')).toBe('Nombre');
    expect(fieldLabel('campoNuevo')).toBe('campoNuevo');
  });
});
