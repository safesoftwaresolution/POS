import { describe, expect, it } from 'vitest';
import { buildDebitNoteRequest, isDefiniteRejection, noteNumber } from './notePayload';

describe('notePayload', () => {
  it('builds one adjustment line that references the validated invoice', () => {
    const body = buildDebitNoteRequest({
      reference: 'ref-1', concept: '1', invoice: { legalNumber: 'SETP10' }, observation: ' Mora ',
      description: ' Intereses ', quantity: '1', price: '100.50', taxRate: '19',
    });
    expect(body).toMatchObject({ reference_code: 'ref-1', customization_id: '30', bill_number: 'SETP10', observation: 'Mora' });
    expect(body.items).toEqual([{
      code_reference: 'ADJUSTMENT', name: 'Intereses', quantity: '1', price: '100.50', discount_rate: '0',
      unit_measure_code: '94', standard_code: '999', taxes: [{ code: '01', rate: '19' }],
    }]);
  });

  it('treats only a 400 as a definite rejection', () => {
    expect(isDefiniteRejection({ status: 400 })).toBe(true);
    expect(isDefiniteRejection({ status: 502 })).toBe(false);
    expect(isDefiniteRejection(new Error('Tiempo de espera agotado'))).toBe(false);
  });

  it('names a note by its legal number, then its reference, then its id', () => {
    expect(noteNumber({ id: 3, creditNoteNumber: 'NC1', referenceCode: 'r' })).toBe('NC1');
    expect(noteNumber({ id: 3, debitNoteNumber: 'ND1' })).toBe('ND1');
    expect(noteNumber({ id: 3, referenceCode: 'r' })).toBe('r');
    expect(noteNumber({ id: 3 })).toBe('#3');
  });
});
