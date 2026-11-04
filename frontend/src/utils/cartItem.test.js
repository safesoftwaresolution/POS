import { describe, expect, it } from 'vitest';
import { cartKey, isService, toSaleLine } from './cartItem';

describe('cartItem', () => {
  it('distingue un producto y un servicio con el mismo id', () => {
    expect(cartKey({ id: 3 })).toBe('PRODUCT-3');
    expect(cartKey({ id: 3, itemType: 'PRODUCT' })).toBe('PRODUCT-3');
    expect(cartKey({ id: 3, itemType: 'SERVICE' })).toBe('SERVICE-3');
  });

  it('isService solo es verdadero para servicios', () => {
    expect(isService({ itemType: 'SERVICE' })).toBe(true);
    expect(isService({ itemType: 'PRODUCT' })).toBe(false);
    expect(isService(undefined)).toBe(false);
  });

  it('envía productId o serviceId, nunca ambos', () => {
    expect(toSaleLine({ id: 7, quantity: 2, price: 1000 }))
      .toEqual({ productId: 7, serviceId: null, quantity: 2, unitPrice: 1000 });
    expect(toSaleLine({ id: 9, itemType: 'SERVICE', quantity: 1, price: 119000 }))
      .toEqual({ productId: null, serviceId: 9, quantity: 1, unitPrice: 119000 });
  });
});
