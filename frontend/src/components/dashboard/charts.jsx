import React, { useId, useState } from 'react';
import { areaPath, pointsFor, smoothPath } from './chartMath';

const LINE_PROPS = { fill: 'none', stroke: 'currentColor', strokeWidth: 2.5, strokeLinecap: 'round', vectorEffect: 'non-scaling-stroke' };

/** Cuántas etiquetas del eje X caben: todas con pocas barras, una de cada N cuando son muchas. */
function labelStep(count) {
  return count <= 8 ? 1 : Math.ceil(count / 7);
}

/**
 * Gráfica de área con curva suave, degradado y tooltip al pasar el cursor o el dedo.
 * `data`: `[{ key, label, value, detail }]`; `formatValue` da formato al monto del tooltip.
 */
export function AreaChart({ data, compare, formatValue, className = 'h-52' }) {
  const gradientId = useId();
  const [hovered, setHovered] = useState(null);
  const values = data.map((d) => d.value);
  const max = Math.max(...values, ...(compare || []), 0);
  const points = pointsFor(values, max);
  const comparePoints = compare ? pointsFor(compare, max) : null;
  const peak = max > 0 ? values.indexOf(max) : -1;
  const step = labelStep(data.length);

  const move = (e) => {
    const rect = e.currentTarget.getBoundingClientRect();
    if (rect.width === 0 || data.length === 0) return;
    const ratio = (e.clientX - rect.left) / rect.width;
    setHovered(Math.min(data.length - 1, Math.max(0, Math.round(ratio * (data.length - 1)))));
  };

  const active = hovered ?? (peak >= 0 ? peak : null);
  const activePoint = active != null ? points[active] : null;
  let alignClass = '-translate-x-1/2';
  if (activePoint && activePoint.x < 18) alignClass = 'translate-x-0';
  else if (activePoint && activePoint.x > 82) alignClass = '-translate-x-full';

  return (
    <div className="pt-10">
      <div
        className={`relative ${className} touch-pan-y`}
        onPointerMove={move}
        onPointerDown={move}
        onPointerLeave={() => setHovered(null)}
      >
        {[25, 50, 75].map((top) => (
          <div key={top} className="absolute inset-x-0 border-t border-dashed border-line" style={{ top: `${top}%` }} />
        ))}

        <svg viewBox="0 0 100 100" preserveAspectRatio="none" className="absolute inset-0 w-full h-full text-brand overflow-visible chart-reveal" aria-hidden="true">
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="currentColor" stopOpacity="0.35" />
              <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
            </linearGradient>
          </defs>
          <path d={areaPath(points)} fill={`url(#${gradientId})`} />
          {comparePoints && <path d={smoothPath(comparePoints)} {...LINE_PROPS} strokeWidth={1.5} strokeDasharray="4 4" className="text-ink-muted opacity-60" />}
          <path d={smoothPath(points)} {...LINE_PROPS} />
        </svg>

        {activePoint && (
          <>
            <div className="absolute top-0 bottom-0 w-px bg-brand/30 pointer-events-none" style={{ left: `${activePoint.x}%` }} />
            <span
              className="absolute w-3.5 h-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full bg-card border-[3px] border-brand shadow pointer-events-none"
              style={{ left: `${activePoint.x}%`, top: `${activePoint.y}%` }}
            />
            <div
              className={`absolute -top-1 ${alignClass} -translate-y-full z-10 pointer-events-none bg-solid dark:bg-white text-solid-ink dark:text-gray-950 rounded-xl px-3 py-1.5 shadow-lg whitespace-nowrap`}
              style={{ left: `${activePoint.x}%` }}
            >
              <div className="text-[10px] font-semibold opacity-70 first-letter:uppercase">{data[active].detail}</div>
              <div className="text-xs font-black tabular-nums">{formatValue(data[active].value)}</div>
              {compare && <div className="text-[10px] font-semibold opacity-70 tabular-nums">Antes: {formatValue(compare[active] ?? 0)}</div>}
            </div>
          </>
        )}
      </div>

      <div className="relative mt-2 h-4">
        {data.map((d, i) => (i % step === 0 || i === data.length - 1) && (
          <span
            key={d.key}
            className={`absolute -translate-x-1/2 text-[10px] font-bold ${i === active ? 'text-brand' : 'text-ink-soft'}`}
            style={{ left: `${points[i].x}%` }}
          >
            {d.label}
          </span>
        ))}
      </div>
    </div>
  );
}

/** Mini gráfica de tendencia sin ejes ni interacción, para acompañar un número. */
export function Sparkline({ values, className = 'h-9' }) {
  const gradientId = useId();
  const points = pointsFor(values);
  if (values.length < 2 || Math.max(...values) <= 0) {
    return <div className={`${className} flex items-end`}><div className="w-full border-t border-dashed border-line" /></div>;
  }
  return (
    <svg viewBox="0 0 100 100" preserveAspectRatio="none" className={`w-full ${className} text-brand overflow-visible chart-reveal`} aria-hidden="true">
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.25" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={areaPath(points)} fill={`url(#${gradientId})`} />
      <path d={smoothPath(points)} {...LINE_PROPS} strokeWidth={2} />
    </svg>
  );
}

/** Dona de segmentos `[{ label, value, color }]`; `children` se dibuja en el centro. */
export function Donut({ segments, children, className = 'w-40 h-40' }) {
  const total = segments.reduce((sum, s) => sum + s.value, 0);
  const gap = segments.length > 1 ? 1.2 : 0;
  const arcs = segments.map((s, i) => {
    const before = segments.slice(0, i).reduce((sum, p) => sum + (total > 0 ? (p.value / total) * 100 : 0), 0);
    const length = total > 0 ? (s.value / total) * 100 : 0;
    return { ...s, length: Math.max(length - gap, 0.1), offset: 25 - before };
  });
  return (
    <div className={`relative ${className} shrink-0`}>
      <svg viewBox="0 0 42 42" className="w-full h-full" aria-hidden="true">
        <circle cx="21" cy="21" r="15.915" fill="none" strokeWidth="5" className="stroke-gray-100 dark:stroke-white/10" />
        {arcs.map((a) => (
          <circle
            key={a.label}
            cx="21"
            cy="21"
            r="15.915"
            fill="none"
            strokeWidth="5"
            strokeLinecap="round"
            stroke={a.color}
            strokeDasharray={`${a.length} ${100 - a.length}`}
            strokeDashoffset={a.offset}
            className="donut-seg"
          />
        ))}
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center text-center">{children}</div>
    </div>
  );
}
