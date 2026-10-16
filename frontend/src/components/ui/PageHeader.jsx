import React from 'react';

/** Título de página + subtítulo, con zona de acciones a la derecha. */
export default function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 className="text-2xl font-black text-ink">{title}</h1>
        {subtitle && <p className="text-xs text-ink-soft font-medium">{subtitle}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}
