import React, { useState } from 'react';
import { Banknote, HandCoins, Settings2 } from 'lucide-react';
import { Badge, Button, EmptyState, Modal } from '../ui';
import Pager from '../ui/Pager';
import CreditPaymentModal from './CreditPaymentModal';
import CreditLimitModal from './CreditLimitModal';
import { getCreditAccountApi, getCreditMovementsApi } from '../../services/creditApi';
import useLoad from '../../hooks/useLoad';
import usePermissions from '../../hooks/usePermissions';
import { formatDateTime, formatLongDate, money, paymentLabel } from '../../utils/format';

const TYPES = {
  CHARGE: { label: 'Fiado', tone: 'warning', sign: '+' },
  PAYMENT: { label: 'Abono', tone: 'success', sign: '−' },
  RETURN: { label: 'Devolución', tone: 'info', sign: '−' },
};

const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, number: 0 };

/** Estado de cuenta de un cliente: cuánto debe, su cupo y el libro de movimientos, con abonar y fijar cupo. */
export default function CreditAccountModal({ customerId, onClose, onChanged }) {
  const { canManageStock: canSetLimit } = usePermissions();
  const [page, setPage] = useState(0);
  const [paying, setPaying] = useState(false);
  const [editingLimit, setEditingLimit] = useState(false);

  const { data: account, reload: reloadAccount } = useLoad(() => getCreditAccountApi(customerId), [customerId], { initial: null });
  const { data: movements, reload: reloadMovements } = useLoad(
    () => getCreditMovementsApi(customerId, page),
    [customerId, page],
    { initial: EMPTY_PAGE },
  );

  const refresh = () => { reloadAccount(); reloadMovements(); onChanged?.(); };

  if (!account) return null;
  const balance = Number(account.balance || 0);

  return (
    <>
      <Modal title={account.customerName} subtitle={account.identification} onClose={onClose} size="lg">
        <div className="grid grid-cols-3 gap-3">
          <Stat label="Debe" value={money(balance)} strong />
          <Stat label="Cupo" value={account.creditLimit != null ? money(account.creditLimit) : 'Sin límite'} />
          <Stat label="Disponible" value={account.availableCredit != null ? money(account.availableCredit) : '—'} />
        </div>

        <div className="flex flex-wrap gap-2">
          <Button icon={Banknote} onClick={() => setPaying(true)} disabled={balance <= 0}>Registrar abono</Button>
          {canSetLimit && <Button variant="outline" icon={Settings2} onClick={() => setEditingLimit(true)}>Cupo</Button>}
        </div>

        {movements.content.length === 0 ? (
          <EmptyState icon={HandCoins} title="Sin movimientos" description="Aquí aparecerán los fiados y abonos de este cliente." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="text-[10px] uppercase tracking-wider text-ink-muted border-b border-line">
                  <th className="py-2 pr-3">Fecha</th>
                  <th className="py-2 pr-3">Movimiento</th>
                  <th className="py-2 pr-3 text-right">Monto</th>
                  <th className="py-2 pr-3 text-right">Saldo</th>
                  <th className="py-2">Detalle</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {movements.content.map((m) => {
                  const t = TYPES[m.type] || { label: m.type, tone: 'neutral', sign: '' };
                  return (
                    <tr key={m.id}>
                      <td className="py-2.5 pr-3 whitespace-nowrap text-ink-soft">{formatDateTime(m.createdAt)}</td>
                      <td className="py-2.5 pr-3"><Badge tone={t.tone}>{t.label}</Badge></td>
                      <td className="py-2.5 pr-3 text-right font-bold tabular-nums text-ink">{t.sign}{money(m.amount)}</td>
                      <td className="py-2.5 pr-3 text-right tabular-nums text-ink-soft">{money(m.balanceAfter)}</td>
                      <td className="py-2.5 text-ink-soft">
                        {m.saleId && <span>Venta #{m.saleId}</span>}
                        {m.dueDate && <span> · vence {formatLongDate(m.dueDate)}</span>}
                        {m.paymentMethod && <span>{paymentLabel(m.paymentMethod)}</span>}
                        {m.note && <span> · {m.note}</span>}
                        {m.username && <span className="text-ink-muted"> · {m.username}</span>}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <Pager page={page} totalPages={movements.totalPages} totalElements={movements.totalElements} onChange={setPage} />
      </Modal>

      {paying && (
        <CreditPaymentModal account={account} onClose={() => setPaying(false)} onSaved={() => { setPaying(false); refresh(); }} />
      )}
      {editingLimit && (
        <CreditLimitModal account={account} onClose={() => setEditingLimit(false)} onSaved={() => { setEditingLimit(false); refresh(); }} />
      )}
    </>
  );
}

function Stat({ label, value, strong }) {
  return (
    <div className="p-3 rounded-2xl bg-page border border-line">
      <span className="block text-[10px] font-extrabold uppercase tracking-wider text-ink-muted">{label}</span>
      <span className={`block tabular-nums text-ink ${strong ? 'text-xl font-black' : 'text-sm font-bold'}`}>{value}</span>
    </div>
  );
}
