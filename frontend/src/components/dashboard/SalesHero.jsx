import React from 'react';
import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react';
import useCountUp from '../../hooks/useCountUp';
import { formatNumber, money } from '../../utils/format';
import { AreaChart, Sparkline } from './charts';
import { percentChange } from './chartMath';

function ChangePill({ change, label }) {
  if (change == null) {
    return <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-page text-[11px] font-bold text-ink-muted"><Minus className="w-3 h-3" /> Sin período anterior</span>;
  }
  const up = change >= 0;
  const Icon = up ? ArrowUpRight : ArrowDownRight;
  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-extrabold ${
      up ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400' : 'bg-red-500/10 text-red-600 dark:text-red-400'
    }`}>
      <Icon className="w-3.5 h-3.5" /> {up ? '+' : ''}{change.toFixed(1)} % <span className="font-semibold opacity-70">{label}</span>
    </span>
  );
}

function MiniStat({ title, value, hint, spark, onClick }) {
  return (
    <button type="button" onClick={onClick} className="text-left p-2.5 sm:p-4 rounded-2xl hover:bg-page transition-colors cursor-pointer min-w-0 flex flex-col">
      <span className="text-[9px] sm:text-[10px] font-extrabold uppercase tracking-wider text-ink-muted leading-tight">{title}</span>
      <div className="text-base sm:text-xl font-black text-ink tabular-nums mt-0.5 truncate">{value}</div>
      <div className="mt-2 flex-1 flex items-end">{spark ? <Sparkline values={spark} /> : <div className="h-9" />}</div>
      <span className="text-[10px] sm:text-[11px] font-semibold text-ink-soft mt-1 leading-tight">{hint}</span>
    </button>
  );
}

/**
 * Bloque principal del Dashboard: ventas del período en grande, variación frente al período anterior,
 * curva por día y tres métricas con su tendencia. `daily` trae `{ key, label, detail, value, count }` por día.
 */
export default function SalesHero({ days, loading, totalSold, previousTotal, daily, transactions, averageTicket, dianCount, onNavigate }) {
  const animatedTotal = useCountUp(totalSold);
  const animatedCount = useCountUp(transactions);
  const animatedTicket = useCountUp(averageTicket);
  const change = percentChange(totalSold, previousTotal);

  return (
    <div className="bento-card hover:transform-none p-6 sm:p-7 card-rise">
      <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
        <div>
          <span className="text-[11px] font-extrabold uppercase tracking-wider text-ink-muted">Ventas · últimos {days} días</span>
          <div className="text-4xl sm:text-5xl font-black tracking-tight text-ink tabular-nums mt-1">{money(Math.round(animatedTotal))}</div>
        </div>
        <ChangePill change={change} label={`vs ${days} días antes`} />
      </div>

      <div className="mt-2">
        {loading ? (
          <div className="h-52 mt-10 rounded-2xl bg-page animate-pulse" />
        ) : (
          <AreaChart data={daily} formatValue={money} />
        )}
      </div>

      <div className="mt-4 pt-3 border-t border-line grid grid-cols-3 divide-x divide-line">
        <MiniStat
          title="Transacciones"
          value={formatNumber(Math.round(animatedCount))}
          hint="ventas por día"
          spark={daily.map((d) => d.count)}
          onClick={() => onNavigate('/sales')}
        />
        <MiniStat
          title="Ticket promedio"
          value={money(Math.round(animatedTicket))}
          hint="valor medio por venta"
          spark={daily.map((d) => (d.count > 0 ? d.value / d.count : 0))}
          onClick={() => onNavigate('/sales')}
        />
        <MiniStat
          title="Facturas DIAN"
          value={dianCount ?? '—'}
          hint="validadas ante la DIAN"
          onClick={() => onNavigate('/invoicing')}
        />
      </div>
    </div>
  );
}
