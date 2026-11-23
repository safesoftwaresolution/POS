import React from 'react';
import { money } from '../../utils/format';

/** Barras diarias de ingresos (verde) y egresos (rojo) del período; el egreso no incluye costos fijos (no tienen fecha). */
export default function DailyFlowChart({ daily }) {
  const max = Math.max(...daily.map((d) => Math.max(d.income, d.outflow)), 1);
  const bar = (value) => `${value > 0 ? Math.max((value / max) * 100, 3) : 0}%`;

  return (
    <div>
      <div className="flex items-end gap-1 h-40" role="img" aria-label="Ingresos y egresos por día">
        {daily.map((d) => (
          <div
            key={d.date}
            className="flex-1 min-w-0 h-full flex items-end justify-center gap-px"
            title={`${d.date} · Ingresos ${money(d.income)} · Egresos ${money(d.outflow)}`}
          >
            <div className="w-1/2 bg-emerald-500 rounded-t-sm" style={{ height: bar(Math.max(d.income, 0)) }} />
            <div className="w-1/2 bg-red-400 rounded-t-sm" style={{ height: bar(d.outflow) }} />
          </div>
        ))}
      </div>
      <div className="flex justify-between text-[10px] font-bold text-ink-muted mt-2">
        <span>{daily[0]?.date}</span>
        <span className="flex items-center gap-3">
          <span className="flex items-center gap-1"><i className="w-2 h-2 rounded-sm bg-emerald-500" /> Ingresos</span>
          <span className="flex items-center gap-1"><i className="w-2 h-2 rounded-sm bg-red-400" /> Egresos</span>
        </span>
        <span>{daily[daily.length - 1]?.date}</span>
      </div>
    </div>
  );
}
