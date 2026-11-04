import React from 'react';
import { AlertTriangle, CalendarClock, CheckCircle2, ChevronRight, ShoppingBag } from 'lucide-react';
import { money } from '../../utils/format';

const TONES = {
  ok: { box: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400', value: 'text-ink' },
  warn: { box: 'bg-amber-500/10 text-amber-600 dark:text-amber-400', value: 'text-amber-600 dark:text-amber-400' },
  danger: { box: 'bg-red-500/10 text-red-600 dark:text-red-400', value: 'text-red-600 dark:text-red-400' },
  info: { box: 'bg-brand/10 text-brand', value: 'text-ink' },
};

function Segment({ icon: Icon, tone, title, value, hint, onClick }) {
  const colors = TONES[tone];
  return (
    <button type="button" onClick={onClick} className="group flex items-center gap-3.5 p-4 sm:p-5 text-left cursor-pointer hover:bg-page transition-colors min-w-0">
      <div className={`w-11 h-11 rounded-2xl flex items-center justify-center shrink-0 ${colors.box}`}><Icon className="w-5 h-5" /></div>
      <div className="min-w-0 flex-1">
        <span className="text-[10px] font-extrabold uppercase tracking-wider text-ink-muted">{title}</span>
        <div className={`text-xl font-black tabular-nums truncate ${colors.value}`}>{value}</div>
        <span className="text-[11px] font-semibold text-ink-soft block truncate">{hint}</span>
      </div>
      <ChevronRight className="w-4 h-4 text-ink-muted shrink-0 transition-transform group-hover:translate-x-0.5" />
    </button>
  );
}

/**
 * Estado del negocio en una sola tarjeta: stock bajo mínimo, vencimientos y lo invertido en compras.
 * Cada tramo lleva a la pantalla donde se resuelve. `expiring` solo se pasa a quien gestiona inventario.
 */
export default function StatusStrip({ days, lowStockCount, expiring, expiringDays, purchasesTotal, showExpiring, showPurchases, onNavigate }) {
  const expired = expiring.filter((p) => p.status === 'EXPIRED').length;
  const soon = expiring.length - expired;

  let expiryTone = 'ok';
  let expiryValue = 'Todo en orden';
  let expiryHint = `Nada vence en ${expiringDays} días`;
  if (expired > 0) {
    expiryTone = 'danger';
    expiryValue = `${expired} ${expired === 1 ? 'lote vencido' : 'lotes vencidos'}`;
    expiryHint = soon > 0 ? `y ${soon} por vencer` : 'Retíralos de la venta';
  } else if (soon > 0) {
    expiryTone = 'warn';
    expiryValue = `${soon} por vencer`;
    expiryHint = `En los próximos ${expiringDays} días`;
  }

  const segments = [
    <Segment
      key="stock"
      icon={lowStockCount > 0 ? AlertTriangle : CheckCircle2}
      tone={lowStockCount > 0 ? 'warn' : 'ok'}
      title="Stock"
      value={lowStockCount > 0 ? `${lowStockCount} bajo mínimo` : 'Niveles óptimos'}
      hint={lowStockCount > 0 ? 'Reabastécelos para no perder ventas' : 'Ningún producto bajo su mínimo'}
      onClick={() => onNavigate('/inventory')}
    />,
    showExpiring && (
      <Segment key="expiry" icon={expired + soon > 0 ? CalendarClock : CheckCircle2} tone={expiryTone} title="Vencimientos" value={expiryValue} hint={expiryHint} onClick={() => onNavigate('/inventory')} />
    ),
    showPurchases && (
      <Segment key="purchases" icon={ShoppingBag} tone="info" title={`Compras · ${days} días`} value={money(purchasesTotal)} hint="Invertido en mercancía" onClick={() => onNavigate('/inventory')} />
    ),
  ].filter(Boolean);

  return (
    <div
      className={`bento-card hover:transform-none overflow-hidden grid grid-cols-1 divide-y md:divide-y-0 md:divide-x divide-line card-rise ${
        segments.length === 3 ? 'md:grid-cols-3' : segments.length === 2 ? 'md:grid-cols-2' : ''
      }`}
      style={{ '--d': '120ms' }}
    >
      {segments}
    </div>
  );
}
