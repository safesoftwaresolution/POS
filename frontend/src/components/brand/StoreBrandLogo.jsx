import React from 'react';
import { BENTO_PATH, POS_PATH } from '../../constants/brandWordmark';
import { DEFAULT_STORE_THEME } from '../../constants/storeThemes';

const ACCENT = 'var(--color-primary)'; // lo pone el tema del local (StoreTemplateContext); sin tema es el naranja de marca
const LIGHT = '#DADADA';
const DARK = '#0A0A0A';

const R = { rx: 6 };

// Pictograma de cada tipo de local dibujado dentro del cuadro del isotipo (zona útil 21..75 de 96), con las mismas
// formas redondeadas del bento original, el acento del tema y el gris claro de marca. Mismas claves que StoreTheme.
const GLYPHS = {
  DEFAULT: (
    <>
      <rect x="21" y="21" width="24" height="24" {...R} fill={ACCENT} />
      <rect x="51" y="21" width="24" height="24" {...R} fill={LIGHT} />
      <rect x="21" y="51" width="24" height="24" {...R} fill={LIGHT} />
      <rect x="51" y="51" width="24" height="24" {...R} fill="#532011" />
    </>
  ),
  GROCERY: ( // carrito de compras
    <>
      <path d="M21 25H30L36 52H66" fill="none" stroke={LIGHT} strokeWidth="6" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M33 33H73L66 52H37Z" fill={ACCENT} stroke={ACCENT} strokeWidth="4" strokeLinejoin="round" />
      <circle cx="41" cy="67" r="5.5" fill={LIGHT} />
      <circle cx="62" cy="67" r="5.5" fill={LIGHT} />
    </>
  ),
  PHARMACY: ( // cruz con pastilla
    <>
      <rect x="35" y="21" width="22" height="54" {...R} fill={ACCENT} />
      <rect x="21" y="35" width="54" height="22" {...R} fill={ACCENT} />
      <g transform="rotate(-35 62 62)">
        <rect x="46" y="54" width="32" height="16" rx="8" fill={LIGHT} stroke={DARK} strokeWidth="4" />
        <rect x="62" y="54" width="16" height="16" rx="8" fill="#532011" />
      </g>
    </>
  ),
  BAKERY: ( // gorro de panadero
    <>
      <circle cx="35" cy="41" r="11" fill={LIGHT} />
      <circle cx="48" cy="35" r="13" fill={LIGHT} />
      <circle cx="61" cy="41" r="11" fill={LIGHT} />
      <rect x="32" y="46" width="32" height="22" rx="6" fill={ACCENT} />
      <rect x="32" y="62" width="32" height="6" rx="3" fill="#532011" />
    </>
  ),
  STATIONERY: ( // lápiz
    <g transform="rotate(45 48 48)">
      <rect x="41" y="14" width="14" height="11" rx="4" fill={LIGHT} />
      <rect x="41" y="23" width="14" height="38" fill={ACCENT} />
      <path d="M41 61H55L48 77Z" fill={LIGHT} strokeLinejoin="round" />
    </g>
  ),
  HARDWARE: ( // martillo
    <g transform="rotate(35 48 48)">
      <rect x="42" y="30" width="12" height="46" rx="4" fill={LIGHT} />
      <rect x="27" y="19" width="42" height="17" rx="5" fill={ACCENT} />
    </g>
  ),
  FOOD: ( // cloche
    <>
      <path d="M22 58A26 26 0 0 1 74 58Z" fill={ACCENT} />
      <circle cx="48" cy="29" r="5" fill={LIGHT} />
      <rect x="18" y="62" width="60" height="8" rx="4" fill={LIGHT} />
    </>
  ),
  LIQUOR: ( // copa
    <>
      <path d="M32 21H64V40A16 16 0 0 1 32 40Z" fill={ACCENT} />
      <rect x="45" y="54" width="6" height="14" fill={LIGHT} />
      <rect x="33" y="66" width="30" height="7" rx="3.5" fill={LIGHT} />
    </>
  ),
  PETS: ( // huella
    <>
      <ellipse cx="48" cy="60" rx="16" ry="13" fill={ACCENT} />
      <circle cx="30" cy="45" r="6.5" fill={LIGHT} />
      <circle cx="42" cy="34" r="7" fill={LIGHT} />
      <circle cx="54" cy="34" r="7" fill={LIGHT} />
      <circle cx="66" cy="45" r="6.5" fill={LIGHT} />
    </>
  ),
  BEAUTY: ( // tijeras
    <>
      <path d="M38 58L62 23M58 58L34 23" fill="none" stroke={LIGHT} strokeWidth="6" strokeLinecap="round" />
      <circle cx="33" cy="66" r="9" fill="none" stroke={ACCENT} strokeWidth="6" />
      <circle cx="63" cy="66" r="9" fill="none" stroke={ACCENT} strokeWidth="6" />
    </>
  ),
  FASHION: ( // bolsa de compras
    <>
      <path d="M37 38V32A11 11 0 0 1 59 32V38" fill="none" stroke={LIGHT} strokeWidth="6" strokeLinecap="round" />
      <rect x="25" y="36" width="46" height="40" rx="9" fill={ACCENT} />
      <circle cx="39" cy="48" r="3.5" fill={DARK} />
      <circle cx="57" cy="48" r="3.5" fill={DARK} />
    </>
  ),
};

/** Isotipo de marca (cuadro, pictograma y punto de marca). Sin tema reconocido cae al bento original. */
function Isotype({ theme }) {
  return (
    <g>
      <rect width="96" height="96" rx="32" fill={DARK} />
      {GLYPHS[theme] ?? GLYPHS[DEFAULT_STORE_THEME]}
      <circle cx="91.5" cy="4.5" r="10.5" fill="#FFFFFF" />
      <circle cx="91.5" cy="4.5" r="7.5" fill={ACCENT} />
    </g>
  );
}

/**
 * Logo de marca del local: el isotipo de PosBento con el pictograma del tipo de negocio y el nombre "POS" en el acento del
 * tema. `compact` dibuja solo el isotipo (menú colapsado). El color de "Bento" sigue el texto (`text-ink`).
 */
export default function StoreBrandLogo({ theme = DEFAULT_STORE_THEME, compact = false, className = 'h-10' }) {
  if (compact) {
    return (
      <svg viewBox="-6 -6 108 108" role="img" aria-label="PosBento" className={`${className} w-auto shrink-0`}>
        <Isotype theme={theme} />
      </svg>
    );
  }
  return (
    <svg viewBox="-6 -6 353.86 108" role="img" aria-label="PosBento" className={`${className} w-auto shrink-0 text-ink`}>
      <Isotype theme={theme} />
      <path d={POS_PATH} fill={ACCENT} />
      <path d={BENTO_PATH} fill="currentColor" />
    </svg>
  );
}
