import { describe, expect, it } from 'vitest';
import { destinationAfterVerification, isEmailNotVerified } from './authFlow';

describe('destinationAfterVerification', () => {
  it('sends a platform operator to the backoffice', () => {
    expect(destinationAfterVerification({ role: 'SUPER_ADMIN', storeId: null })).toBe('/backoffice');
  });

  it('sends a user who already has a store to the app and one without a store to the store onboarding', () => {
    expect(destinationAfterVerification({ role: 'ADMINISTRATOR', storeId: 4 })).toBe('/');
    expect(destinationAfterVerification({ role: 'SELLER', storeId: 4 })).toBe('/');
    expect(destinationAfterVerification({ role: 'ADMINISTRATOR', storeId: null })).toBe('/onboarding/store');
  });
});

describe('isEmailNotVerified', () => {
  it('recognizes the EmailNotVerified code from the backend, not the message text', () => {
    expect(isEmailNotVerified({ data: { error: 'EmailNotVerified' }, message: 'cualquier texto' })).toBe(true);
    expect(isEmailNotVerified({ data: { error: 'Forbidden' }, message: 'Debes verificar tu correo' })).toBe(false);
    expect(isEmailNotVerified(new Error('sin datos'))).toBe(false);
  });
});
