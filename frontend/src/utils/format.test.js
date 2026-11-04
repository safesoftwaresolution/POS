import { describe, expect, it } from 'vitest';
import { dayEnd, dayStart, formatDateTime, formatLongDate, formatNumber, money, paymentLabel } from './format';

describe('format', () => {
  it('money redondea a pesos sin decimales', () => {
    expect(money(12500)).toBe('$12.500');
    expect(money(12499.6)).toBe('$12.500');
    expect(money(null)).toBe('$0');
    expect(money('abc')).toBe('$0');
  });

  it('formatNumber separa miles', () => {
    expect(formatNumber(1234567)).toBe('1.234.567');
    expect(formatNumber(undefined)).toBe('0');
  });

  it('las fechas sin valor muestran el marcador', () => {
    expect(formatDateTime(null)).toBe('—');
    expect(formatLongDate('')).toBe('—');
    expect(formatLongDate(null, null)).toBeNull();
  });

  it('formatLongDate escribe la fecha en español', () => {
    expect(formatLongDate('2026-10-05T10:00:00')).toMatch(/5 de octubre de 2026/);
  });

  it('dayStart y dayEnd cubren el día completo', () => {
    expect(dayStart('2026-10-05')).toBe('2026-10-05T00:00:00');
    expect(dayEnd('2026-10-05')).toBe('2026-10-05T23:59:59');
  });

  it('paymentLabel traduce los códigos conocidos y deja pasar los demás', () => {
    expect(paymentLabel('CASH')).toBe('Efectivo');
    expect(paymentLabel('CRYPTO')).toBe('CRYPTO');
    expect(paymentLabel(undefined)).toBe('—');
  });
});
