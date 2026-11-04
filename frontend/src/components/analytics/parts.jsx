import React from 'react';
import { money } from '../../utils/format';

/** Fila de barra horizontal de una sola serie (magnitud), con etiqueta directa. */
function RankedBarRow({ label, value, max, formatValue }) {
  const pct = max > 0 ? Math.max(Math.round((value / max) * 100), 4) : 4;
  return (
    <div className="space-y-1" title={`${label}: ${formatValue(value)}`}>
      <div className="flex items-center justify-between text-xs">
        <span className="font-bold text-ink truncate pr-2">{label}</span>
        <span className="font-extrabold text-brand tabular-nums shrink-0">{formatValue(value)}</span>
      </div>
      <div className="h-2.5 w-full bg-page rounded-full overflow-hidden">
        <div className="h-full bg-solid dark:bg-brand rounded-full transition-all duration-500" style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

/** Ranking en barras: `entries` es `[{ label, value }]` ya ordenado. */
export function RankedList({ entries, formatValue = money, emptyLabel = 'Sin datos en este período' }) {
  if (!entries.length) {
    return <p className="text-xs text-ink-muted font-semibold py-6 text-center">{emptyLabel}</p>;
  }
  const max = Math.max(...entries.map((e) => e.value), 1);
  return (
    <div className="space-y-3.5">
      {entries.map((e) => (
        <RankedBarRow key={e.label} label={e.label} value={e.value} max={max} formatValue={formatValue} />
      ))}
    </div>
  );
}

/** Tarjeta con ícono, título y subtítulo para un bloque de analíticas. */
export function SectionCard({ icon: Icon, title, subtitle, children, action }) {
  return (
    <div className="bento-card p-6 bg-card rounded-3xl border border-line shadow-xs">
      <div className="flex items-start justify-between gap-3 mb-5">
        <div className="flex items-center gap-3 min-w-0">
          <div className="w-10 h-10 rounded-full bg-brand/10 flex items-center justify-center text-brand shrink-0">
            <Icon className="w-5 h-5" />
          </div>
          <div className="min-w-0">
            <h3 className="text-sm font-extrabold text-ink truncate">{title}</h3>
            {subtitle && <p className="text-[11px] text-ink-soft font-medium truncate">{subtitle}</p>}
          </div>
        </div>
        {action}
      </div>
      {children}
    </div>
  );
}

export function LoadingRows() {
  return (
    <div className="space-y-3.5 animate-pulse">
      {[0, 1, 2].map((i) => (
        <div key={i} className="space-y-1.5">
          <div className="h-3 w-2/3 bg-gray-200 dark:bg-gray-700 rounded-full" />
          <div className="h-2.5 w-full bg-gray-100 dark:bg-gray-800 rounded-full" />
        </div>
      ))}
    </div>
  );
}
