import { describe, expect, it } from 'vitest';
import { creditBlockReason, defaultCreditDueDate, isGeneralCustomer } from './credit';

describe('credit', () => {
  it('reconoce al Cliente General por su identificación o su nombre', () => {
    expect(isGeneralCustomer(null)).toBe(true);
    expect(isGeneralCustomer({ identification: '222222222222', fullName: 'X' })).toBe(true);
    expect(isGeneralCustomer({ identification: '1', fullName: 'Cliente General' })).toBe(true);
    expect(isGeneralCustomer({ identification: '1020', fullName: 'Ana Pérez' })).toBe(false);
  });

  it('suma 30 días a la fecha, cruzando de mes y de año', () => {
    expect(defaultCreditDueDate(new Date(2026, 0, 15))).toBe('2026-02-14');
    expect(defaultCreditDueDate(new Date(2026, 11, 20))).toBe('2027-01-19');
  });

  it('bloquea el fiado al Cliente General', () => {
    expect(creditBlockReason({ identification: '222222222222' }, null, 1000)).toMatch(/cliente registrado/);
  });

  it('bloquea cuando supera el cupo disponible y deja pasar cuando no hay cupo o alcanza', () => {
    const ana = { identification: '1020', fullName: 'Ana' };
    expect(creditBlockReason(ana, { availableCredit: 5000 }, 6000)).toMatch(/cupo/);
    expect(creditBlockReason(ana, { availableCredit: 5000 }, 5000)).toBeNull();
    expect(creditBlockReason(ana, { availableCredit: null }, 9_000_000)).toBeNull();
    expect(creditBlockReason(ana, null, 1000)).toBeNull();
  });
});
