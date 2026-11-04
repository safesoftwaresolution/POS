import React from 'react';
import { AlertCircle, HandCoins } from 'lucide-react';
import { money } from '../../utils/format';

const SECTION_LABEL = 'text-[10px] font-extrabold uppercase tracking-wider text-ink-muted';

/** Cobro a fiado: a quién se le fía, cuánto debe ya, su cupo y la fecha en que pagaría. */
export default function CreditPanel({
  customers, selectedCustomer, onSelectCustomer, account, total, dueDate, onDueDateChange, blockReason,
}) {
  const debt = Number(account?.balance || 0);
  const hasLimit = account?.creditLimit != null;

  return (
    <div className="p-4 rounded-2xl border border-amber-500/30 bg-amber-50 dark:bg-amber-500/10 space-y-3">
      <div className="flex items-center gap-2 text-xs font-extrabold text-ink">
        <HandCoins className="w-4 h-4 text-amber-600 dark:text-amber-400" /> Venta a fiado
      </div>
      <p className="text-[11px] text-ink-soft">
        No se recibe dinero ahora: el total se suma a lo que el cliente debe y lo irá abonando.
      </p>

      <div className="space-y-1.5">
        <label htmlFor="checkout-credit-customer" className={SECTION_LABEL}>Cliente</label>
        <select
          id="checkout-credit-customer"
          value={selectedCustomer?.id || ''}
          onChange={(e) => onSelectCustomer(customers.find((c) => c.id === parseInt(e.target.value)) || null)}
          className="w-full h-11 px-3 bg-white dark:bg-page border border-line rounded-2xl text-xs font-bold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
        >
          <option value="">Selecciona un cliente…</option>
          {customers.map((c) => (
            <option key={c.id} value={c.id}>{c.fullName || c.name} — {c.identification}</option>
          ))}
        </select>
      </div>

      {account && !blockReason && (
        <div className="p-3 rounded-2xl bg-white dark:bg-page border border-line text-[11px] space-y-1">
          <div className="flex justify-between gap-3">
            <span className="text-ink-soft">Debe hoy</span>
            <span className="font-bold text-ink tabular-nums">{money(debt)}</span>
          </div>
          <div className="flex justify-between gap-3">
            <span className="text-ink-soft">Cupo disponible</span>
            <span className="font-bold text-ink tabular-nums">{hasLimit ? money(account.availableCredit) : 'Sin límite'}</span>
          </div>
          <div className="flex justify-between gap-3 pt-1 border-t border-line">
            <span className="text-ink-soft">Quedaría debiendo</span>
            <span className="font-black text-ink tabular-nums">{money(debt + total)}</span>
          </div>
        </div>
      )}

      <div className="space-y-1.5">
        <label htmlFor="checkout-credit-due" className={SECTION_LABEL}>Fecha límite de pago</label>
        <input
          id="checkout-credit-due"
          type="date"
          value={dueDate}
          onChange={(e) => onDueDateChange(e.target.value)}
          className="w-full h-11 px-3 bg-white dark:bg-page border border-line rounded-2xl text-xs font-bold text-ink focus:outline-none focus:border-ink dark:focus:border-brand"
        />
      </div>

      {blockReason && (
        <p className="text-[11px] text-red-600 dark:text-red-400 font-semibold flex items-start gap-1.5">
          <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-px" /> <span>{blockReason}</span>
        </p>
      )}
    </div>
  );
}
