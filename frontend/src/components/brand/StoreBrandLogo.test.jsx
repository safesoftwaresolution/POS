import React from 'react';
import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import StoreBrandLogo from './StoreBrandLogo';
import { STORE_THEMES } from '../../constants/storeThemes';

const markup = (props) => renderToStaticMarkup(<StoreBrandLogo {...props} />);

describe('StoreBrandLogo', () => {
  it('cada tema dibuja su propio pictograma dentro del isotipo (ninguno repite el de otro)', () => {
    const drawings = Object.keys(STORE_THEMES).map(theme => markup({ theme, compact: true }));
    expect(new Set(drawings).size).toBe(Object.keys(STORE_THEMES).length);
    drawings.forEach(svg => expect(svg).toContain('rx="32"')); // el cuadro de marca siempre está
  });

  it('un tema desconocido o ausente cae al bento original', () => {
    expect(markup({ theme: 'NOPE', compact: true })).toBe(markup({ theme: 'DEFAULT', compact: true }));
    expect(markup({ compact: true })).toBe(markup({ theme: 'DEFAULT', compact: true }));
  });

  it('el logo completo pinta POS con el acento del tema y Bento con el color del texto', () => {
    const svg = markup({ theme: 'BAKERY' });
    expect(svg).toContain('fill="var(--color-primary)"');
    expect(svg).toContain('fill="currentColor"');
    expect(markup({ theme: 'BAKERY', compact: true })).not.toContain('currentColor');
  });
});
