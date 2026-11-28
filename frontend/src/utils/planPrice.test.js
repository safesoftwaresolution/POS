import { expect, it } from 'vitest';
import { formatPlanPrice } from './planPrice';

it('shows "Precio por definir" when the price is not defined', () => {
  expect(formatPlanPrice(null)).toBe('Precio por definir');
  expect(formatPlanPrice(undefined)).toBe('Precio por definir');
  expect(formatPlanPrice('')).toBe('Precio por definir');
});

it('shows "Gratis" for a zero price', () => {
  expect(formatPlanPrice(0)).toBe('Gratis');
});

it('formats any other price as Colombian pesos', () => {
  expect(formatPlanPrice(49900)).toBe('$49.900');
  expect(formatPlanPrice(49900.0)).toBe('$49.900');
});
