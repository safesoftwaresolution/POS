import React from 'react';

/** Isotipo de marca (public/isotipo-color.svg): el punto naranja de marca ya viene dibujado en el SVG. */
export default function BentoIcon({ size = 'w-10 h-10' }) {
  return (
    <img
      src="/isotipo-color.svg"
      alt="PosBento"
      className={`${size} shrink-0 object-contain`}
      draggable={false}
    />
  );
}
