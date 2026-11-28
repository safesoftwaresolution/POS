import React from 'react';
import { Check, Loader2, Pencil } from 'lucide-react';

const money = (n) => n.toLocaleString('es-CO');

/**
 * Confirmacion inline del asistente, en lugar del boton de cobro del ticket: repite lo que se va a registrar (lo mismo
 * que se ve arriba) y espera que el cajero lo confirme o siga editando. Registrar la venta es siempre decision suya.
 * Es una venta normal: nunca se emite factura electronica desde aqui.
 *
 * `summary`: { itemCount, total, customerName, paymentLabel, onCredit, cashReceived, change, cashShort }
 */
export default function AgentConfirmation({ summary, submitting, onConfirm, onCancel }) {
  const { itemCount, total, customerName, paymentLabel, onCredit, cashReceived, change, cashShort } = summary;

  return (
    <div role="group" aria-label="Confirmar venta" className="rounded-2xl border-2 border-brand bg-card p-3.5 space-y-3">
      <div>
        <p className="text-xs font-black text-ink">¿Registro esta venta?</p>
        <p className="text-[11px] font-semibold text-ink-muted">Revisa el ticket de arriba. Hasta que confirmes no se registra nada.</p>
      </div>

      <dl className="space-y-1 text-[11px] font-semibold text-ink-soft">
        <div className="flex justify-between gap-3"><dt>Artículos</dt><dd className="text-ink tabular-nums">{itemCount}</dd></div>
        <div className="flex justify-between gap-3"><dt>Cliente</dt><dd className="text-ink text-right truncate">{customerName}</dd></div>
        <div className="flex justify-between gap-3"><dt>Pago</dt><dd className="text-ink">{paymentLabel}{onCredit ? ' (a crédito)' : ''}</dd></div>
        {cashReceived != null && (
          <div className="flex justify-between gap-3"><dt>Efectivo recibido</dt><dd className="text-ink tabular-nums">${money(cashReceived)}</dd></div>
        )}
        {cashReceived != null && !cashShort && change > 0 && (
          <div className="flex justify-between gap-3"><dt>Cambio</dt><dd className="text-ink tabular-nums">${money(change)}</dd></div>
        )}
        <div className="flex justify-between gap-3 pt-1.5 border-t border-line text-sm font-black text-ink">
          <dt>Total</dt><dd className="text-brand tabular-nums">${money(total)} COP</dd>
        </div>
      </dl>

      {cashShort && (
        <p role="alert" className="text-[11px] font-bold text-red-600 dark:text-red-400">
          El efectivo recibido no cubre el total. Corrígelo con el asistente o registra la venta con el cobro normal.
        </p>
      )}

      <div className="flex gap-2">
        <button
          type="button"
          onClick={onCancel}
          disabled={submitting}
          className="flex-1 py-2.5 rounded-xl border border-line text-xs font-extrabold text-ink-soft hover:text-ink flex items-center justify-center gap-1.5 cursor-pointer disabled:opacity-50"
        >
          <Pencil className="w-3.5 h-3.5" /> Seguir editando
        </button>
        <button
          type="button"
          onClick={onConfirm}
          disabled={submitting || cashShort}
          className="flex-[1.4] py-2.5 rounded-xl bg-brand text-white text-xs font-black flex items-center justify-center gap-1.5 shadow-lg cursor-pointer active:scale-98 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
          Confirmar y registrar
        </button>
      </div>
    </div>
  );
}
