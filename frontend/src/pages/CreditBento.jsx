import React, { useState } from 'react';
import { HandCoins, Search } from 'lucide-react';
import { Badge, Card, EmptyState, PageHeader } from '../components/ui';
import Pager from '../components/ui/Pager';
import CreditAccountModal from '../components/credit/CreditAccountModal';
import { getCreditAccountsApi } from '../services/creditApi';
import useLoad from '../hooks/useLoad';
import useDebouncedValue from '../hooks/useDebouncedValue';
import { formatDateTime, money } from '../utils/format';

const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, number: 0 };

/** Fiado: quién debe cuánto, con su estado de cuenta y los abonos. */
export default function CreditBento() {
  const [search, setSearch] = useState('');
  const [onlyWithBalance, setOnlyWithBalance] = useState(true);
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState(null);
  const debouncedSearch = useDebouncedValue(search);

  const { data, loading, reload } = useLoad(
    () => getCreditAccountsApi({ search: debouncedSearch.trim(), onlyWithBalance, page }),
    [debouncedSearch, onlyWithBalance, page],
    { initial: EMPTY_PAGE, autoRefresh: true },
  );

  return (
    <div className="space-y-6">
      <PageHeader
        title="Fiado"
        subtitle="Quién debe cuánto y los abonos a su cuenta"
        actions={data.totalElements > 0 && (
          <Badge tone="warning">{data.totalElements} {data.totalElements === 1 ? 'cuenta' : 'cuentas'}</Badge>
        )}
      />

      <Card className="space-y-4">
        <div className="flex flex-col sm:flex-row gap-3 sm:items-center">
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-ink-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(0); }}
              placeholder="Buscar por nombre o identificación"
              aria-label="Buscar cliente"
              className="w-full pl-10 pr-4 py-2.5 bg-page border border-line rounded-2xl text-xs font-semibold text-ink focus:outline-none focus:border-ink"
            />
          </div>
          <label className="flex items-center gap-2 text-xs font-bold text-ink-soft cursor-pointer">
            <input
              type="checkbox"
              checked={onlyWithBalance}
              onChange={(e) => { setOnlyWithBalance(e.target.checked); setPage(0); }}
            />
            Solo con deuda
          </label>
        </div>

        {!loading && data.content.length === 0 ? (
          <EmptyState
            icon={HandCoins}
            title={onlyWithBalance ? 'Nadie debe nada' : 'Sin cuentas de fiado'}
            description="Cuando vendas con el método Fiado en el punto de venta, el cliente aparecerá aquí."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="text-[10px] uppercase tracking-wider text-ink-muted border-b border-line">
                  <th className="py-2 pr-3">Cliente</th>
                  <th className="py-2 pr-3 text-right">Debe</th>
                  <th className="py-2 pr-3 text-right">Cupo</th>
                  <th className="py-2">Último movimiento</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {data.content.map((a) => (
                  <tr
                    key={a.customerId}
                    onClick={() => setSelectedId(a.customerId)}
                    className="cursor-pointer hover:bg-gray-50 dark:hover:bg-white/5"
                  >
                    <td className="py-3 pr-3">
                      <span className="block font-bold text-ink">{a.customerName}</span>
                      <span className="block text-[11px] text-ink-muted">{a.identification}</span>
                    </td>
                    <td className="py-3 pr-3 text-right font-black tabular-nums text-ink">{money(a.balance)}</td>
                    <td className="py-3 pr-3 text-right tabular-nums text-ink-soft">
                      {a.creditLimit != null ? money(a.creditLimit) : 'Sin límite'}
                    </td>
                    <td className="py-3 text-ink-soft">{formatDateTime(a.lastMovementAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Pager page={page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
      </Card>

      {selectedId && (
        <CreditAccountModal customerId={selectedId} onClose={() => setSelectedId(null)} onChanged={reload} />
      )}
    </div>
  );
}
