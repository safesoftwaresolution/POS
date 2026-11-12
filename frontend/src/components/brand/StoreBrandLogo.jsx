import React from 'react';
import { BENTO_PATH, POS_PATH } from '../../constants/brandWordmark';

const ACCENT = 'var(--color-primary)'; // naranja de marca
const LIGHT = '#DADADA';
const DARK = '#0A0A0A';

const R = { rx: 6 };

/** Isotipo de marca: el cuadro bento original con su punto de marca. */
function Isotype() {
  return (
    <g>
      <rect width="96" height="96" rx="32" fill={DARK} />
      <rect x="21" y="21" width="24" height="24" {...R} fill={ACCENT} />
      <rect x="51" y="21" width="24" height="24" {...R} fill={LIGHT} />
      <rect x="21" y="51" width="24" height="24" {...R} fill={LIGHT} />
      <rect x="51" y="51" width="24" height="24" {...R} fill="#532011" />
      <circle cx="91.5" cy="4.5" r="10.5" fill="#FFFFFF" />
      <circle cx="91.5" cy="4.5" r="7.5" fill={ACCENT} />
    </g>
  );
}

/**
 * Logo de PosBento: el isotipo y el nombre "POS" en el acento de marca. `compact` dibuja solo el isotipo (menú
 * colapsado). El color de "Bento" sigue el texto (`text-ink`).
 */
export default function StoreBrandLogo({ compact = false, className = 'h-10' }) {
  if (compact) {
    return (
      <svg viewBox="-6 -6 108 108" role="img" aria-label="PosBento" className={`${className} w-auto shrink-0`}>
        <Isotype />
      </svg>
    );
  }
  return (
    <svg viewBox="-6 -6 353.86 108" role="img" aria-label="PosBento" className={`${className} w-auto shrink-0 text-ink`}>
      <Isotype />
      <path d={POS_PATH} fill={ACCENT} />
      <path d={BENTO_PATH} fill="currentColor" />
    </svg>
  );
}
