import React from 'react';
import { Plus, Receipt } from 'lucide-react';
import { Button } from '../ui';
import { money, paymentLabel } from '../../utils/format';

const SHOWN = 6;

/** Últimas ventas como lista con avatar, cliente, hora y monto. */
export default function RecentSales({ loading, sales, onNavigate, className = '' }) {
  return (
    <div className={`bento-card hover:transform-none p-6 card-rise ${className}`} style={{ '--d': '240ms' }}>
      <div className="flex justify-between items-center gap-3 mb-4">
        <div>
          <h3 className="text-sm font-extrabold text-ink">Ventas recientes</h3>
          <p className="text-[11px] text-ink-soft font-medium">Las últimas {SHOWN}</p>
        </div>
        <Button variant="soft" size="sm" onClick={() => onNavigate('/sales')}>Ver todas</Button>
      </div>

      {loading ? (
        <div className="space-y-3 animate-pulse">{[0, 1, 2, 3].map((i) => <div key={i} className="h-12 rounded-xl bg-page" />)}</div>
      ) : sales.length === 0 ? (
        <div className="py-8 text-center space-y-3">
          <div className="w-12 h-12 mx-auto rounded-full bg-page text-ink-muted flex items-center justify-center"><Receipt className="w-5 h-5" /></div>
          <p className="text-xs font-semibold text-ink-soft">No hay ventas en este período</p>
          <Button size="sm" icon={Plus} onClick={() => onNavigate('/pos')}>Nueva venta</Button>
        </div>
      ) : (
        <ul className="divide-y divide-line">
          {sales.slice(0, SHOWN).map((s) => (
            <li key={s.id} className="flex items-center gap-3 py-3">
              <span className="w-9 h-9 rounded-full bg-brand/10 text-brand flex items-center justify-center shrink-0"><Receipt className="w-4 h-4" /></span>
              <div className="flex-1 min-w-0">
                <p className="text-xs font-bold text-ink truncate">{s.customerName || 'Cliente General'}</p>
                <p className="text-[11px] text-ink-soft truncate">
                  <span className="font-semibold text-brand">{s.invoiceNumber || `#${s.id}`}</span>
                  {' · '}
                  {s.createdAt ? new Date(s.createdAt).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'}
                  {s.paymentMethod ? ` · ${paymentLabel(s.paymentMethod)}` : ''}
                </p>
              </div>
              <span className="text-xs font-black text-ink tabular-nums shrink-0">{money(s.totalAmount)}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
