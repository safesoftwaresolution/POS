import { describe, expect, it } from 'vitest';
import { templatesOf, toggleTemplate } from './templateSelection';

describe('toggleTemplate', () => {
  it('agrega y quita en el orden canónico, sin mutar la lista original', () => {
    const base = ['APPOINTMENTS'];
    expect(toggleTemplate(base, 'RETAIL')).toEqual(['RETAIL', 'APPOINTMENTS']);
    expect(toggleTemplate(['RETAIL', 'APPOINTMENTS'], 'RETAIL')).toEqual(['APPOINTMENTS']);
    expect(base).toEqual(['APPOINTMENTS']);
  });
});

describe('templatesOf', () => {
  it('usa la lista y, si la respuesta es vieja, la plantilla principal', () => {
    expect(templatesOf({ templates: ['RETAIL', 'APPOINTMENTS'], template: 'APPOINTMENTS' })).toEqual(['RETAIL', 'APPOINTMENTS']);
    expect(templatesOf({ template: 'FOOD' })).toEqual(['FOOD']);
    expect(templatesOf(undefined)).toEqual([]);
  });
});
