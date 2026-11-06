import { describe, expect, it } from 'vitest';
import { gettingStartedSteps, isBusinessProfileComplete } from './gettingStarted';

describe('isBusinessProfileComplete', () => {
  it('is complete once name, address and phone are real values', () => {
    expect(isBusinessProfileComplete({ businessName: 'Tienda 1', address: 'Calle 1', phone: '300' })).toBe(true);
  });

  it('is incomplete with the backend placeholders, blanks or no settings', () => {
    expect(isBusinessProfileComplete({ businessName: 'Mi Negocio', address: 'Calle 1', phone: '300' })).toBe(false);
    expect(isBusinessProfileComplete({ businessName: 'Tienda 1', address: 'Por definir', phone: '300' })).toBe(false);
    expect(isBusinessProfileComplete({ businessName: 'Tienda 1', address: 'Calle 1', phone: ' ' })).toBe(false);
    expect(isBusinessProfileComplete(null)).toBe(false);
  });
});

describe('gettingStartedSteps', () => {
  const base = { hasProducts: false, hasSales: false, canCreateProducts: true };

  it('adds the business data and the optional e-invoicing steps for the administrator', () => {
    const steps = gettingStartedSteps({ ...base, isAdmin: true, profileComplete: false, billingActive: false });
    expect(steps.map((s) => s.label)).toEqual([
      'Completa los datos de tu negocio',
      'Crea tu primer producto',
      'Haz tu primera venta',
      'Activa la facturación electrónica (opcional)',
    ]);
    expect(steps.find((s) => s.optional).to).toBe('/invoicing?tab=activation');
    expect(steps[0].to).toBe('/settings');
  });

  it('leaves out the administrator steps for other roles and the steps whose state is unknown', () => {
    expect(gettingStartedSteps({ ...base, isAdmin: false }).map((s) => s.to)).toEqual(['/products', '/pos']);
    const unknown = gettingStartedSteps({ ...base, isAdmin: true, profileComplete: null, billingActive: null });
    expect(unknown.map((s) => s.to)).toEqual(['/products', '/pos']);
  });
});
