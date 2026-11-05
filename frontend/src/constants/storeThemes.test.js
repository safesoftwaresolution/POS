import { describe, expect, it } from 'vitest';
import { DEFAULT_STORE_THEME, STORE_THEMES, storeThemeFor } from './storeThemes';

const luminance = (hex) => {
  const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map(c => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const contrast = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

// Mismas claves que el enum StoreTheme del backend.
const BACKEND_KEYS = ['DEFAULT', 'GROCERY', 'PHARMACY', 'BAKERY', 'STATIONERY', 'HARDWARE', 'FOOD', 'LIQUOR', 'PETS', 'BEAUTY', 'FASHION'];
const WHITE = '#FFFFFF';
const DARK_CARD = '#17171B'; // --bg-card en modo oscuro (index.css)
const BRAND = STORE_THEMES.DEFAULT.accent;

describe('STORE_THEMES', () => {
  it('tiene exactamente los temas del backend', () => {
    expect(Object.keys(STORE_THEMES).sort()).toEqual([...BACKEND_KEYS].sort());
  });

  it('el predeterminado es el naranja de marca de DESIGN.md', () => {
    expect(STORE_THEMES.DEFAULT.accent).toBe('#FF5520');
    expect(STORE_THEMES.DEFAULT.hover).toBe('#E64712');
  });

  it.each(Object.entries(STORE_THEMES))('%s: el acento no contrasta peor que el naranja de marca, en claro y en oscuro', (_key, theme) => {
    expect(theme.accent).toMatch(/^#[0-9A-F]{6}$/);
    expect(theme.hover).toMatch(/^#[0-9A-F]{6}$/);
    expect(contrast(theme.accent, WHITE)).toBeGreaterThanOrEqual(contrast(BRAND, WHITE) - 0.01);
    expect(contrast(theme.accent, DARK_CARD)).toBeGreaterThanOrEqual(4.5);
  });

  it('cada tema tiene etiqueta e icono', () => {
    for (const theme of Object.values(STORE_THEMES)) {
      expect(theme.label).toBeTruthy();
      expect(theme.icon).toBeTruthy();
    }
  });
});

describe('storeThemeFor', () => {
  it('una clave desconocida o ausente cae al predeterminado', () => {
    expect(storeThemeFor('GROCERY').label).toBe('Minimercado');
    expect(storeThemeFor('NEON')).toBe(STORE_THEMES[DEFAULT_STORE_THEME]);
    expect(storeThemeFor(undefined)).toBe(STORE_THEMES[DEFAULT_STORE_THEME]);
  });
});
