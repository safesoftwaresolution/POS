import React from 'react';
import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import StoreBrandLogo from './StoreBrandLogo';

const markup = (props) => renderToStaticMarkup(<StoreBrandLogo {...props} />);

describe('StoreBrandLogo', () => {
  it('el isotipo compacto dibuja el cuadro de marca con su bento', () => {
    const svg = markup({ compact: true });
    expect(svg).toContain('rx="32"');
    expect(svg).not.toContain('currentColor');
  });

  it('el logo completo pinta POS con el acento de marca y Bento con el color del texto', () => {
    const svg = markup({});
    expect(svg).toContain('fill="var(--color-primary)"');
    expect(svg).toContain('fill="currentColor"');
  });
});
