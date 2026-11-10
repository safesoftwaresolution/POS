import {
  Store, ShoppingBasket, Pill, Croissant, PenLine, Wrench, UtensilsCrossed, Wine, PawPrint, Scissors, Shirt,
} from 'lucide-react';

// Tema visual de un local. Lo deriva el backend de la categoría del local; aquí viven las claves (las mismas del enum
// StoreTheme) con su acento e icono. Solo cambia el acento (`--color-primary` y su hover): neutros, formas, tipografía y modo
// claro/oscuro siguen siendo los de DESIGN.md. Cada acento queda en la banda de luminancia donde cumple, en claro y en
// oscuro, lo que ya cumple el naranja de marca (texto blanco >= 3,2:1 y texto sobre la tarjeta oscura >= 4,5:1);
// `storeThemes.test.js` lo comprueba.
export const DEFAULT_STORE_THEME = 'DEFAULT';

export const STORE_THEMES = {
  DEFAULT: { label: 'Predeterminado', icon: Store, accent: '#FF5520', hover: '#E64712' },
  GROCERY: { label: 'Minimercado', icon: ShoppingBasket, accent: '#23964D', hover: '#1F8444' },
  PHARMACY: { label: 'Farmacia', icon: Pill, accent: '#1A9386', hover: '#178176' },
  BAKERY: { label: 'Panadería', icon: Croissant, accent: '#C66B1C', hover: '#AE5E19' },
  STATIONERY: { label: 'Papelería', icon: PenLine, accent: '#3A80EF', hover: '#3371D2' },
  HARDWARE: { label: 'Ferretería', icon: Wrench, accent: '#A47D09', hover: '#906E08' },
  FOOD: { label: 'Restaurante', icon: UtensilsCrossed, accent: '#E84B45', hover: '#CC423D' },
  LIQUOR: { label: 'Licorera', icon: Wine, accent: '#9E69DC', hover: '#8B5CC2' },
  PETS: { label: 'Mascotas', icon: PawPrint, accent: '#E14694', hover: '#C63E82' },
  BEAUTY: { label: 'Belleza', icon: Scissors, accent: '#C551D6', hover: '#AD47BC' },
  FASHION: { label: 'Moda', icon: Shirt, accent: '#EA436D', hover: '#CE3B60' },
};

export const STORE_THEME_OPTIONS = Object.entries(STORE_THEMES).map(([value, { label }]) => ({ value, label }));

/** Tema de una clave; una desconocida o ausente cae al predeterminado. */
export const storeThemeFor = (key) => STORE_THEMES[key] ?? STORE_THEMES[DEFAULT_STORE_THEME];
