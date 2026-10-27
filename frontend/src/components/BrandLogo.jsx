import React from 'react';

/** Logo horizontal de marca: variante para fondo claro u oscuro según el tema (Tailwind `dark:`). */
export default function BrandLogo({ className = 'h-9' }) {
  return (
    <>
      <img
        src="/logo-horizontal-color-fondo-claro.svg"
        alt="PosBento"
        className={`${className} w-auto shrink-0 dark:hidden`}
        draggable={false}
      />
      <img
        src="/logo-horizontal-color-fondo-oscuro.svg"
        alt="PosBento"
        className={`${className} w-auto shrink-0 hidden dark:block`}
        draggable={false}
      />
    </>
  );
}
