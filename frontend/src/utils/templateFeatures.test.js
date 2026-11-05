import { describe, expect, it } from 'vitest';
import { templateFeaturesFor } from './templateFeatures';
import { storeTemplateLabel } from '../constants/storeTemplates';

describe('templateFeaturesFor', () => {
  it('solo la plantilla APPOINTMENTS habilita las citas', () => {
    expect(templateFeaturesFor('APPOINTMENTS').appointments).toBe(true);
    expect(templateFeaturesFor('RETAIL').appointments).toBe(false);
    expect(templateFeaturesFor('FOOD').appointments).toBe(false);
  });

  it('una plantilla desconocida o ausente se comporta como RETAIL', () => {
    expect(templateFeaturesFor(undefined)).toEqual({ template: 'RETAIL', appointments: false });
    expect(templateFeaturesFor('SPA')).toEqual({ template: 'RETAIL', appointments: false });
  });
});

describe('storeTemplateLabel', () => {
  it('muestra la etiqueta y cae a la de RETAIL si la clave no existe', () => {
    expect(storeTemplateLabel('APPOINTMENTS')).toBe('Citas y agenda');
    expect(storeTemplateLabel('FOOD')).toBe('Comida y bebidas');
    expect(storeTemplateLabel(undefined)).toBe('Comercio / Tienda');
  });
});
