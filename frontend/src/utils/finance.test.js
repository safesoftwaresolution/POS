import { describe, expect, it } from 'vitest';
import {
  dueLabel, fixedCostFormError, fixedCostPayload, formatVariation, movementFormError, movementPayload, nextDueDate,
} from './finance';

const form = {
  type: 'EXPENSE', category: ' Servicios ', description: ' ', amount: '180000', paymentMethod: '', movementDate: '2026-11-20',
};

describe('finance', () => {
  it('formatea la variación con signo, una cifra decimal y su dirección', () => {
    expect(formatVariation(12.5)).toEqual({ text: '+12,5 %', direction: 'up' });
    expect(formatVariation(-3)).toEqual({ text: '-3,0 %', direction: 'down' });
    expect(formatVariation(0)).toEqual({ text: '0,0 %', direction: 'flat' });
    expect(formatVariation(undefined).direction).toBe('flat');
  });

  it('arma el cuerpo del movimiento: recorta, usa efectivo por defecto y manda null sin descripción', () => {
    expect(movementPayload(form)).toEqual({
      type: 'EXPENSE', category: 'Servicios', description: null, amount: 180000, paymentMethod: 'CASH', movementDate: '2026-11-20',
    });
    expect(movementPayload({ ...form, type: 'INCOME' }).type).toBe('INCOME');
  });

  it('valida el movimiento: tipo, categoría, monto y fecha no futura', () => {
    expect(movementFormError(form, '2026-11-23')).toBe('');
    expect(movementFormError({ ...form, type: '' }, '2026-11-23')).toMatch(/egreso o un ingreso/);
    expect(movementFormError({ ...form, category: ' ' }, '2026-11-23')).toMatch(/categoría/);
    expect(movementFormError({ ...form, amount: '0' }, '2026-11-23')).toMatch(/mayor a 0/);
    expect(movementFormError({ ...form, movementDate: '2026-11-24' }, '2026-11-23')).toMatch(/futura/);
  });

  it('arma y valida el costo fijo, con el día de cobro opcional', () => {
    const cost = { name: ' Arriendo ', monthlyAmount: '1500000', dueDay: '5', active: true };
    expect(fixedCostPayload(cost)).toEqual({ name: 'Arriendo', monthlyAmount: 1500000, dueDay: 5, active: true });
    expect(fixedCostPayload({ ...cost, dueDay: '' }).dueDay).toBeNull();
    expect(fixedCostFormError(cost)).toBe('');
    expect(fixedCostFormError({ ...cost, dueDay: '' })).toBe('');
    expect(fixedCostFormError({ ...cost, name: '' })).toMatch(/nombre/);
    expect(fixedCostFormError({ ...cost, monthlyAmount: '' })).toMatch(/mayor a 0/);
    expect(fixedCostFormError({ ...cost, dueDay: '0' })).toMatch(/entre 1 y 31/);
    expect(fixedCostFormError({ ...cost, dueDay: '32' })).toMatch(/entre 1 y 31/);
    expect(fixedCostFormError({ ...cost, dueDay: '2.5' })).toMatch(/entre 1 y 31/);
  });

  it('calcula la próxima fecha de cobro: este mes si falta, el siguiente si ya pasó, y hoy cuenta', () => {
    expect(nextDueDate(null)).toBeNull();
    expect(nextDueDate(25, new Date(2026, 10, 23))).toEqual({ date: '2026-11-25', daysLeft: 2 });
    expect(nextDueDate(23, new Date(2026, 10, 23))).toEqual({ date: '2026-11-23', daysLeft: 0 });
    expect(nextDueDate(5, new Date(2026, 10, 23))).toEqual({ date: '2026-12-05', daysLeft: 12 });
    expect(nextDueDate(5, new Date(2026, 11, 23))).toEqual({ date: '2027-01-05', daysLeft: 13 });
  });

  it('en un mes corto el día 31 vale el último día', () => {
    expect(nextDueDate(31, new Date(2026, 1, 10))).toEqual({ date: '2026-02-28', daysLeft: 18 });
    expect(nextDueDate(31, new Date(2026, 3, 30))).toEqual({ date: '2026-04-30', daysLeft: 0 });
    expect(nextDueDate(31, new Date(2026, 4, 1))).toEqual({ date: '2026-05-31', daysLeft: 30 });
  });

  it('describe el vencimiento en palabras', () => {
    expect(dueLabel(0)).toBe('Hoy');
    expect(dueLabel(1)).toBe('Mañana');
    expect(dueLabel(7)).toBe('En 7 días');
  });
});
