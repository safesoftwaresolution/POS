import React from 'react';
import { CreditCard } from 'lucide-react';
import { money } from '../../utils/format';
import { Donut } from './charts';

// Paleta categórica: el naranja de marca primero y tonos que se distinguen en tema claro y oscuro.
const COLORS = ['#FF5520', '#6366F1', '#10B981', '#F59E0B', '#94A3B8', '#EC4899'];

/** Mezcla de medios de pago como dona con el total al centro y la leyenda al lado. `entries`: `[{ label, value, pct }]`. */
export default function PaymentsCard({ days, loading, entries, total }) {
  const segments = entries.map((e, i) => ({ ...e, color: COLORS[i % COLORS.length] }));
  return (
    <div className="bento-card hover:transform-none p-6 card-rise" style={{ '--d': '80ms' }}>
      <div className="flex items-center gap-3 mb-5">
        <div className="w-10 h-10 rounded-full bg-brand/10 flex items-center justify-center text-brand shrink-0"><CreditCard className="w-5 h-5" /></div>
        <div className="min-w-0">
          <h3 className="text-sm font-extrabold text-ink">Métodos de pago</h3>
          <p className="text-[11px] text-ink-soft font-medium">Cobros de los últimos {days} días</p>
        </div>
      </div>

      {loading ? (
        <div className="h-40 rounded-2xl bg-page animate-pulse" />
      ) : segments.length === 0 ? (
        <p className="text-xs text-ink-muted font-semibold py-10 text-center">Aún no hay cobros en este período</p>
      ) : (
        <div className="flex flex-col items-center gap-5">
          <Donut segments={segments}>
            <span className="text-[10px] font-extrabold uppercase tracking-wider text-ink-muted">Total</span>
            <span className="text-base font-black text-ink tabular-nums">{money(total)}</span>
          </Donut>
          <ul className="w-full space-y-2.5">
            {segments.map((s) => (
              <li key={s.label} className="flex items-center gap-2.5 text-xs">
                <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: s.color }} />
                <span className="font-bold text-ink flex-1 truncate">{s.label}</span>
                <span className="font-semibold text-ink-soft tabular-nums">{money(s.value)}</span>
                <span className="font-black text-ink tabular-nums w-10 text-right">{s.pct}%</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
