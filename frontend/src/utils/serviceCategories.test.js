import { describe, expect, it } from 'vitest';
import { categoryOptionsFor, offeredToLabel, toggleId } from './serviceCategories';

describe('toggleId', () => {
  it('agrega y quita sin mutar la lista', () => {
    const base = [1];
    expect(toggleId(base, 2)).toEqual([1, 2]);
    expect(toggleId([1, 2], 1)).toEqual([2]);
    expect(base).toEqual([1]);
  });
});

describe('offeredToLabel', () => {
  const stores = [{ id: 1, name: 'Barbería' }, { id: 2, name: 'Boutique' }];
  it('sin vínculos se ofrece a todos los locales', () => {
    expect(offeredToLabel({ storeCategoryIds: [] }, stores)).toBe('Todos los locales');
  });
  it('lista los tipos de local por nombre e ignora los desconocidos', () => {
    expect(offeredToLabel({ storeCategoryIds: [1, 2, 9] }, stores)).toBe('Barbería, Boutique');
  });
});

describe('categoryOptionsFor', () => {
  const available = [{ id: 1, name: 'Corte' }];
  it('conserva la categoría actual del servicio aunque ya no se ofrezca', () => {
    expect(categoryOptionsFor(available, { id: 5, name: 'Vieja' })).toEqual([...available, { id: 5, name: 'Vieja' }]);
  });
  it('no duplica la actual si ya está disponible y no agrega nada sin categoría', () => {
    expect(categoryOptionsFor(available, { id: 1, name: 'Corte' })).toBe(available);
    expect(categoryOptionsFor(available, null)).toBe(available);
  });
});
