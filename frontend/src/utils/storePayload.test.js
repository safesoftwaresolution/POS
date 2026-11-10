import { describe, expect, it } from 'vitest';
import { storeUpdatePayload } from './storePayload';

const store = {
  id: 4, name: 'Barbería Central', storeCategoryId: 3, phone: '3001112233', email: 'b@central.co',
  website: 'https://central.co', address: 'Calle 1 # 2-3', taxId: '900123', status: 'ACTIVE', planCode: 'PRO',
};

describe('storeUpdatePayload', () => {
  it('conserva los datos del local y solo cambia la categoría', () => {
    expect(storeUpdatePayload(store, '10')).toEqual({
      name: 'Barbería Central', storeCategoryId: 10, phone: '3001112233', email: 'b@central.co',
      website: 'https://central.co', address: 'Calle 1 # 2-3', taxId: '900123',
    });
  });

  it('una categoría vacía deja el local sin categoría', () => {
    expect(storeUpdatePayload(store, '').storeCategoryId).toBeNull();
  });

  it('no manda los campos ausentes como undefined (el backend los recibiría como null)', () => {
    const payload = storeUpdatePayload({ id: 1, name: 'X', email: 'x@x.co' }, '2');
    expect(payload).toEqual({ name: 'X', storeCategoryId: 2, phone: '', email: 'x@x.co', website: '', address: '', taxId: '' });
  });
});
