import React from 'react';
import { inputClass } from './Field';
import { isoDaysAgo } from '../../utils/format';

const PRESETS = [
  { label: 'Hoy', range: () => [isoDaysAgo(0), isoDaysAgo(0)] },
  { label: '7 días', range: () => [isoDaysAgo(6), isoDaysAgo(0)] },
  { label: 'Este mes', range: () => [`${isoDaysAgo(0).slice(0, 8)}01`, isoDaysAgo(0)] },
];

/** Rango de fechas con atajos (Hoy, 7 días, Este mes). `onChange(from, to)` recibe fechas ISO yyyy-MM-dd. */
export default function DateRangeFilter({ from, to, onChange }) {
  const today = isoDaysAgo(0);
  return (
    <div className="flex flex-col gap-2">
      <div className="grid grid-cols-2 sm:flex sm:items-center gap-2">
        <label className="flex flex-col sm:flex-row sm:items-center gap-1 text-[10px] font-extrabold uppercase text-ink-muted">
          Desde
          <input type="date" value={from} max={to} onChange={(e) => onChange(e.target.value, to)} className={`${inputClass} w-full sm:w-auto`} />
        </label>
        <label className="flex flex-col sm:flex-row sm:items-center gap-1 text-[10px] font-extrabold uppercase text-ink-muted">
          Hasta
          <input type="date" value={to} min={from} max={today} onChange={(e) => onChange(from, e.target.value)} className={`${inputClass} w-full sm:w-auto`} />
        </label>
      </div>
      <div className="flex flex-wrap gap-1.5">
        {PRESETS.map((p) => {
          const [f, t] = p.range();
          const active = f === from && t === to;
          return (
            <button
              key={p.label}
              type="button"
              onClick={() => onChange(f, t)}
              className={`px-3 py-1 text-xs font-bold rounded-full border cursor-pointer transition-colors ${
                active ? 'bg-solid text-white border-solid' : 'bg-card text-ink-soft border-line hover:text-ink'
              }`}
            >
              {p.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
