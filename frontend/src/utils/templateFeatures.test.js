import { describe, expect, it } from 'vitest';
import { templateFeaturesFor } from './templateFeatures';
import { storeTemplateLabel } from '../constants/storeTemplates';

describe('templateFeaturesFor', () => {
  it('las citas se habilitan si cualquiera de las plantillas es APPOINTMENTS', () => {
    expect(templateFeaturesFor(['APPOINTMENTS']).appointments).toBe(true);
    expect(templateFeaturesFor(['RETAIL', 'APPOINTMENTS']).appointments).toBe(true);
    expect(templateFeaturesFor(['RETAIL']).appointments).toBe(false);
    expect(templateFeaturesFor(['RETAIL', 'FOOD']).appointments).toBe(false);
  });

  it('acepta una sola plantilla como texto (respuesta vieja del backend)', () => {
    expect(templateFeaturesFor('APPOINTMENTS').appointments).toBe(true);
    expect(templateFeaturesFor('FOOD')).toEqual({ templates: ['FOOD'], template: 'FOOD', appointments: false });
  });

  it('la principal es APPOINTMENTS > FOOD > RETAIL', () => {
    expect(templateFeaturesFor(['RETAIL', 'FOOD', 'APPOINTMENTS']).template).toBe('APPOINTMENTS');
    expect(templateFeaturesFor(['RETAIL', 'FOOD']).template).toBe('FOOD');
  });

  it('una plantilla desconocida o ausente se comporta como RETAIL', () => {
    const retail = { templates: ['RETAIL'], template: 'RETAIL', appointments: false };
    expect(templateFeaturesFor(undefined)).toEqual(retail);
    expect(templateFeaturesFor([])).toEqual(retail);
    expect(templateFeaturesFor(['SPA'])).toEqual(retail);
    expect(templateFeaturesFor(['SPA', 'APPOINTMENTS']).appointments).toBe(true);
  });
});

describe('storeTemplateLabel', () => {
  it('muestra la etiqueta y cae a la de RETAIL si la clave no existe', () => {
    expect(storeTemplateLabel('APPOINTMENTS')).toBe('Citas y agenda');
    expect(storeTemplateLabel('FOOD')).toBe('Comida y bebidas');
    expect(storeTemplateLabel(undefined)).toBe('Comercio / Tienda');
  });
});
