import React from 'react';

/** Lista numerada de pasos a seguir fuera del sistema (portales de la DIAN). */
export default function GuideList({ items }) {
  return (
    <ol className="space-y-2">
      {items.map((item, i) => (
        // Los pasos son fijos: el índice es una clave estable.
        <li key={i} className="flex items-start gap-3 text-xs text-ink-soft">
          <span className="w-6 h-6 rounded-full bg-solid text-solid-ink text-[11px] font-black flex items-center justify-center shrink-0">
            {i + 1}
          </span>
          <span className="pt-0.5 leading-relaxed">{item}</span>
        </li>
      ))}
    </ol>
  );
}
