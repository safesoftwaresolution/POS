import React, { useMemo, useState } from 'react';
import { formatNumber, money } from '../../utils/format';

const DAYS = [
  { id: 1, short: 'L', name: 'lunes' },
  { id: 2, short: 'M', name: 'martes' },
  { id: 3, short: 'X', name: 'miércoles' },
  { id: 4, short: 'J', name: 'jueves' },
  { id: 5, short: 'V', name: 'viernes' },
  { id: 6, short: 'S', name: 'sábado' },
  { id: 7, short: 'D', name: 'domingo' },
];
// Franja que siempre se muestra aunque no haya ventas, para que el mapa no quede en una sola columna.
const MIN_FROM = 8;
const MIN_TO = 20;

const hourLabel = (hour) => `${hour % 12 === 0 ? 12 : hour % 12} ${hour < 12 ? 'am' : 'pm'}`;
const slotLabel = (day, hour) => `${DAYS[day - 1].name} ${hourLabel(hour)}`;

/**
 * Mapa de calor de ventas: una fila por día de la semana y una columna por hora; cuanto más oscuro, más se vendió.
 * `cells`: `[{ dayOfWeek, hourOfDay, transactionCount, totalSold }]` (solo franjas con ventas).
 */
export default function SalesHeatmap({ cells }) {
  const [hovered, setHovered] = useState(null);

  const { grid, hours, best, max } = useMemo(() => {
    const byKey = new Map(cells.map((c) => [`${c.dayOfWeek}-${c.hourOfDay}`, { ...c, totalSold: Number(c.totalSold) }]));
    const hoursWithSales = cells.map((c) => c.hourOfDay);
    const from = Math.min(MIN_FROM, ...hoursWithSales);
    const to = Math.max(MIN_TO, ...hoursWithSales);
    const range = Array.from({ length: to - from + 1 }, (_, i) => from + i);
    let top = null;
    byKey.forEach((c) => { if (!top || c.totalSold > top.totalSold) top = c; });
    return { grid: byKey, hours: range, best: top, max: top ? top.totalSold : 0 };
  }, [cells]);

  if (cells.length === 0) {
    return <p className="text-xs text-ink-muted font-semibold py-10 text-center">Aún no hay ventas para mostrar el mapa</p>;
  }

  const shown = hovered ?? best;

  return (
    <div className="space-y-4">
      <div className="rounded-2xl bg-page px-4 py-3 text-xs min-h-[3.25rem]">
        <span className="text-[10px] font-extrabold uppercase tracking-wider text-ink-muted block">
          {hovered ? 'Franja seleccionada' : 'Tu mejor momento'}
        </span>
        <span className="font-black text-ink first-letter:uppercase inline-block">{slotLabel(shown.dayOfWeek, shown.hourOfDay)}</span>
        <span className="text-ink-soft font-semibold"> · {formatNumber(shown.transactionCount)} {shown.transactionCount === 1 ? 'venta' : 'ventas'} · {money(shown.totalSold)}</span>
      </div>

      <div className="overflow-x-auto -mx-1 px-1 pb-1">
        <div className="min-w-[420px]">
          <div className="grid gap-1" style={{ gridTemplateColumns: `1.25rem repeat(${hours.length}, minmax(0, 1fr))` }}>
            <span />
            {hours.map((h) => (
              <span key={h} className="text-[9px] font-bold text-ink-muted text-center">{h % 2 === 0 ? hourLabel(h).replace(' ', '') : ''}</span>
            ))}
            {DAYS.map((day) => (
              <React.Fragment key={day.id}>
                <span className="text-[10px] font-extrabold text-ink-soft flex items-center">{day.short}</span>
                {hours.map((h) => {
                  const cell = grid.get(`${day.id}-${h}`);
                  const intensity = cell && max > 0 ? cell.totalSold / max : 0;
                  const isBest = best && cell === best;
                  return (
                    <button
                      key={h}
                      type="button"
                      aria-label={cell ? `${slotLabel(day.id, h)}: ${cell.transactionCount} ventas, ${money(cell.totalSold)}` : `${slotLabel(day.id, h)}: sin ventas`}
                      onMouseEnter={() => cell && setHovered(cell)}
                      onMouseLeave={() => setHovered(null)}
                      onFocus={() => cell && setHovered(cell)}
                      onBlur={() => setHovered(null)}
                      onClick={() => cell && setHovered(cell)}
                      className={`aspect-square rounded-md transition-transform hover:scale-125 focus-visible:scale-125 focus:outline-none ${
                        cell ? 'bg-brand cursor-pointer' : 'bg-page cursor-default'
                      } ${isBest ? 'ring-2 ring-ink ring-offset-1 ring-offset-card' : ''}`}
                      style={cell ? { opacity: 0.18 + intensity * 0.82 } : undefined}
                    />
                  );
                })}
              </React.Fragment>
            ))}
          </div>
        </div>
      </div>

      <div className="flex items-center justify-end gap-2 text-[10px] font-semibold text-ink-muted">
        Menos
        {[0.18, 0.4, 0.6, 0.8, 1].map((o) => <span key={o} className="w-3.5 h-3.5 rounded-md bg-brand" style={{ opacity: o }} />)}
        Más
      </div>
    </div>
  );
}
