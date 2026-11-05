import { describe, expect, it } from 'vitest';
import { extraTemplatesToSave, templatesOf, toggleTemplate } from './templateSelection';

describe('toggleTemplate', () => {
  it('agrega y quita en el orden canónico, sin mutar la lista original', () => {
    const base = ['APPOINTMENTS'];
    expect(toggleTemplate(base, 'RETAIL')).toEqual(['RETAIL', 'APPOINTMENTS']);
    expect(toggleTemplate(['RETAIL', 'APPOINTMENTS'], 'RETAIL')).toEqual(['APPOINTMENTS']);
    expect(base).toEqual(['APPOINTMENTS']);
  });
});

describe('extraTemplatesToSave', () => {
  it('no guarda como extra lo que ya aporta la categoría', () => {
    expect(extraTemplatesToSave(['RETAIL', 'APPOINTMENTS'], ['RETAIL'])).toEqual(['APPOINTMENTS']);
  });

  it('sin extras marcados devuelve una lista vacía (quita los que había)', () => {
    expect(extraTemplatesToSave(['RETAIL'], ['RETAIL'])).toEqual([]);
  });

  it('un local sin categoría guarda todo lo marcado', () => {
    expect(extraTemplatesToSave(['APPOINTMENTS', 'FOOD'], [])).toEqual(['FOOD', 'APPOINTMENTS']);
  });
});

describe('templatesOf', () => {
  it('usa la lista y, si la respuesta es vieja, la plantilla principal', () => {
    expect(templatesOf({ templates: ['RETAIL', 'APPOINTMENTS'], template: 'APPOINTMENTS' })).toEqual(['RETAIL', 'APPOINTMENTS']);
    expect(templatesOf({ template: 'FOOD' })).toEqual(['FOOD']);
    expect(templatesOf(undefined)).toEqual([]);
  });
});
