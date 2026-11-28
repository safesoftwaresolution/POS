import { money } from './format';

/** Plan price for display: no value → "Precio por definir", 0 → "Gratis", otherwise pesos ("$49.900"). */
export function formatPlanPrice(value) {
  if (value === null || value === undefined || value === '') return 'Precio por definir';
  if (Number(value) === 0) return 'Gratis';
  return money(value);
}
