import React, { useMemo, useState } from 'react';
import { RotateCcw, Trash2, TriangleAlert } from 'lucide-react';
import Modal from '../ui/Modal';
import Button from '../ui/Button';
import Badge from '../ui/Badge';
import MoneyInput from '../ui/MoneyInput';
import { inputClass } from '../ui/Field';
import { cx } from '../ui/cx';
import { cartKey, isService } from '../../utils/cartItem';
import { paymentLabel } from '../../utils/format';
import {
  buildTicketFromDraft,
  candidateKey,
  customerName,
  hasUndecided,
  initialDecisions,
  parseQuantity,
  selectableCustomerCandidates,
  selectableItemCandidates,
  selectablePaymentCandidates,
} from '../../utils/voiceDraft';

const SECTION_LABEL = 'text-[10px] font-extrabold uppercase tracking-wider text-ink-muted';
const PENDING = 'border-amber-400 dark:border-amber-500/60';

const STATUS_BADGE = {
  RESOLVED: { tone: 'success', label: 'Coincide' },
  AMBIGUOUS: { tone: 'warning', label: 'Varias opciones' },
  NEEDS_REVIEW: { tone: 'warning', label: 'Por revisar' },
  NOT_FOUND: { tone: 'danger', label: 'No encontrado' },
  NOT_PROVIDED: { tone: 'neutral', label: 'No mencionado' },
};

function StatusBadge({ status }) {
  const { tone, label } = STATUS_BADGE[status] ?? STATUS_BADGE.NOT_PROVIDED;
  return <Badge tone={tone}>{label}</Badge>;
}

// Texto de existencias del producto elegido, para que el cajero vea el tope antes de aplicar.
function stockHint(product) {
  if (!product) return null;
  if (isService(product)) return 'Servicio';
  const stock = product.quantityAvailable ?? product.stock ?? 0;
  return stock <= 0 ? 'Sin stock' : `Stock: ${stock}`;
}

/**
 * Revisión de una venta dictada. Solo prepara el ticket: `onApply(ticket)` recibe el resultado de
 * `buildTicketFromDraft` (carrito, cliente, medio de pago y efectivo) y el cobro lo hace el cajero con el flujo normal.
 * Nada aquí crea una venta ni llama al backend. Lo ambiguo se decide a mano; nada se elige por el cajero.
 */
export default function VoiceDraftModal({
  draft, productsList, customersList, paymentMethods, cart, customer, paymentMethod, onApply, onClose,
}) {
  const [decisions, setDecisions] = useState(() => initialDecisions(draft, { paymentMethods }));

  const ticket = useMemo(
    () => buildTicketFromDraft({ draft, decisions, productsList, customersList, paymentMethods, cart, customer, paymentMethod }),
    [draft, decisions, productsList, customersList, paymentMethods, cart, customer, paymentMethod],
  );
  const undecided = hasUndecided(draft, decisions);
  const canApply = !undecided && !ticket.isEmpty;
  const showCash = (ticket.paymentMethod ?? paymentMethod) === 'CASH';

  const setItem = (index, patch) => setDecisions((d) => ({
    ...d,
    items: d.items.map((item, i) => (i === index ? { ...item, ...patch } : item)),
  }));
  // Cliente y medio de pago comparten el mismo select: '' = sin decidir, 'keep' = conservar el del ticket, otro = candidato.
  const setSingle = (field, value) => setDecisions((d) => ({
    ...d,
    [field]: value === '' ? { mode: 'pending', choice: null }
      : value === 'keep' ? { mode: 'keep', choice: null }
        : { mode: 'pick', choice: value },
  }));
  const singleValue = ({ mode, choice }) => (mode === 'pick' ? choice : mode === 'keep' ? 'keep' : '');

  const customerCandidates = selectableCustomerCandidates(draft.customer);
  const paymentCandidates = selectablePaymentCandidates(draft.paymentMethod, paymentMethods);
  const currentCustomer = customerName(customer);

  const apply = () => { if (canApply) onApply(ticket); };

  return (
    <Modal
      title="Revisar venta dictada"
      subtitle="Es solo una propuesta: no se registra ninguna venta hasta que cobres desde el ticket."
      size="lg"
      dismissible={false}
      onClose={onClose}
      footer={(
        <>
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button variant="brand" disabled={!canApply} onClick={apply}>Aplicar al ticket</Button>
        </>
      )}
    >
      <section className="space-y-2">
        <h4 className={SECTION_LABEL}>Productos y servicios</h4>
        {draft.items.length === 0 ? (
          <p className="text-xs text-ink-soft">No se mencionó ningún producto ni servicio.</p>
        ) : (
          <ul className="space-y-2">
            {draft.items.map(({ item }, index) => {
              const decision = decisions.items[index];
              const selectable = selectableItemCandidates(item);
              const chosen = selectable.find((c) => candidateKey(c) === decision.choice);
              const product = chosen && productsList.find((p) => cartKey(p) === candidateKey(chosen));
              const quantityInvalid = parseQuantity(decision.quantity) === null;
              const single = selectable.length === 1 && item.status === 'RESOLVED';
              return (
                <li key={index} className="rounded-2xl border border-line bg-page p-3 space-y-2">
                  <div className="flex items-start justify-between gap-2">
                    <p className="text-xs font-bold text-ink min-w-0 break-words">{`«${item.mention}»`}</p>
                    <div className="flex items-center gap-2 shrink-0">
                      <StatusBadge status={item.status} />
                      {selectable.length > 0 && (decision.dropped ? (
                        <button
                          type="button"
                          onClick={() => setItem(index, { dropped: false })}
                          aria-label={`Restaurar «${item.mention}»`}
                          className="p-1.5 rounded-xl text-ink-muted hover:text-ink hover:bg-gray-100 dark:hover:bg-white/5 cursor-pointer"
                        >
                          <RotateCcw className="w-3.5 h-3.5" />
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => setItem(index, { dropped: true })}
                          aria-label={`Quitar «${item.mention}»`}
                          className="p-1.5 rounded-xl text-ink-muted hover:text-red-600 hover:bg-gray-100 dark:hover:bg-white/5 cursor-pointer"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      ))}
                    </div>
                  </div>

                  {decision.dropped ? (
                    <p className="text-[11px] text-ink-muted">No se agregará al ticket.</p>
                  ) : (
                    <div className="grid gap-2 sm:grid-cols-[1fr_6rem] sm:items-start">
                      <div className="space-y-1 min-w-0">
                        {single ? (
                          <p className="text-xs font-semibold text-ink">{selectable[0].label}</p>
                        ) : (
                          <select
                            aria-label={`Producto para «${item.mention}»`}
                            value={decision.choice ?? ''}
                            onChange={(e) => setItem(index, { choice: e.target.value || null })}
                            className={cx(inputClass, decision.choice == null && PENDING)}
                          >
                            {decision.choice == null && <option value="">Elegir producto o servicio…</option>}
                            {selectable.map((c) => <option key={candidateKey(c)} value={candidateKey(c)}>{c.label}</option>)}
                          </select>
                        )}
                        {stockHint(product) && <p className="text-[11px] text-ink-muted">{stockHint(product)}</p>}
                      </div>
                      <input
                        type="text"
                        inputMode="numeric"
                        autoComplete="off"
                        aria-label={`Cantidad de «${item.mention}»`}
                        placeholder="Cantidad"
                        value={decision.quantity}
                        onChange={(e) => setItem(index, { quantity: e.target.value.replace(/\D/g, '') })}
                        className={cx(inputClass, 'tabular-nums', quantityInvalid && PENDING)}
                      />
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <section className="space-y-2">
        <h4 className={SECTION_LABEL}>Cliente</h4>
        {customerCandidates.length > 0 ? (
          <select
            aria-label="Cliente de la venta"
            value={singleValue(decisions.customer)}
            onChange={(e) => setSingle('customer', e.target.value)}
            className={cx(inputClass, decisions.customer.mode === 'pending' && PENDING)}
          >
            {decisions.customer.mode === 'pending' && <option value="">Elegir cliente…</option>}
            <option value="keep">{`Mantener: ${currentCustomer}`}</option>
            {customerCandidates.map((c) => <option key={candidateKey(c)} value={candidateKey(c)}>{c.label}</option>)}
          </select>
        ) : (
          <p className="text-xs text-ink-soft">{`Sin cambios: se mantiene ${currentCustomer}.`}</p>
        )}
      </section>

      <section className="space-y-2">
        <h4 className={SECTION_LABEL}>Medio de pago</h4>
        {paymentCandidates.length > 0 ? (
          <select
            aria-label="Medio de pago"
            value={singleValue(decisions.paymentMethod)}
            onChange={(e) => setSingle('paymentMethod', e.target.value)}
            className={cx(inputClass, decisions.paymentMethod.mode === 'pending' && PENDING)}
          >
            {decisions.paymentMethod.mode === 'pending' && <option value="">Elegir medio de pago…</option>}
            <option value="keep">{`Mantener: ${paymentLabel(paymentMethod)}`}</option>
            {paymentCandidates.map((c) => <option key={candidateKey(c)} value={candidateKey(c)}>{c.label}</option>)}
          </select>
        ) : (
          <p className="text-xs text-ink-soft">{`Sin cambios: se mantiene ${paymentLabel(paymentMethod)}.`}</p>
        )}

        {showCash && (
          <MoneyInput
            label="Efectivo recibido"
            hint="Opcional: también se puede ajustar al cobrar."
            placeholder="0"
            value={decisions.cashReceived}
            onChange={(value) => setDecisions((d) => ({ ...d, cashReceived: value }))}
          />
        )}
      </section>

      {ticket.warnings.length > 0 && (
        <ul className="space-y-1.5 rounded-2xl border border-amber-200 bg-amber-50 dark:border-amber-500/20 dark:bg-amber-500/10 p-3">
          {ticket.warnings.map((w, i) => (
            <li key={`${w.code}-${i}`} className="flex items-start gap-2 text-xs font-semibold text-amber-700 dark:text-amber-400">
              <TriangleAlert className="w-3.5 h-3.5 mt-0.5 shrink-0" />
              <span>{w.message}</span>
            </li>
          ))}
        </ul>
      )}

      {undecided && (
        <p role="status" className="text-[11px] font-semibold text-ink-soft">
          Resuelve los datos marcados para poder aplicar el borrador.
        </p>
      )}
      {!undecided && ticket.isEmpty && (
        <p role="status" className="text-[11px] font-semibold text-ink-soft">No hay nada que aplicar al ticket.</p>
      )}
    </Modal>
  );
}
