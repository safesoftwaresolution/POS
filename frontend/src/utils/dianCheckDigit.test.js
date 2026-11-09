import { describe, expect, it } from 'vitest';
import { calculateDv, formatNitWithDv } from './dianCheckDigit';

// Mismos valores que DianCheckDigitTest del backend: los dos lados deben calcular el mismo DV.
describe('calculateDv', () => {
  it('calcula el dígito de verificación de NIT conocidos', () => {
    expect(calculateDv('800197268')).toBe('4');
    expect(calculateDv('900123456')).toBe('8');
  });

  it('los restos 0 y 1 son el propio dígito', () => {
    expect(calculateDv('0')).toBe('0');
    expect(calculateDv('4')).toBe('1');
    expect(calculateDv('1')).toBe('8');
  });

  it('ignora puntos, guiones y espacios', () => {
    expect(calculateDv('800.197.268')).toBe('4');
    expect(calculateDv(' 900-123-456 ')).toBe('8');
  });

  it('sin dígitos o con más de 15 no hay DV', () => {
    expect(calculateDv('')).toBe('');
    expect(calculateDv(null)).toBe('');
    expect(calculateDv(' - ')).toBe('');
    expect(calculateDv('1234567890123456')).toBe('');
  });
});

describe('formatNitWithDv', () => {
  it('muestra el NIT con su DV', () => {
    expect(formatNitWithDv('900123456')).toBe('900123456-8');
    expect(formatNitWithDv('')).toBe('');
  });
});
