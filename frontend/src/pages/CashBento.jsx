import React, { useMemo, useState } from 'react';
import { Wallet, RefreshCw, CheckCircle2, MinusCircle, PlusCircle, CreditCard, Users } from 'lucide-react';
import { getCashClosingReportApi } from '../services/reportsApi';
import useLoad from '../hooks/useLoad';
import { Button, DateRangeFilter, MoneyInput, PageHeader } from '../components/ui';
import { LoadingRows, RankedList, SectionCard } from '../components/analytics/parts';
import sortedEntries from '../components/analytics/sortedEntries';
import { dayEnd, dayStart, isoDaysAgo, money, paymentLabel } from '../utils/format';

/** Caja: efectivo esperado del período, arqueo contra lo contado y desglose por cajero y método de pago. */
export default function CashBento() {
  const [dateFrom, setDateFrom] = useState(isoDaysAgo(0));
  const [dateTo, setDateTo] = useState(isoDaysAgo(0));
  const [countedCash, setCountedCash] = useState('');

  const { data: closing, loading, error, reload } = useLoad(
    () => getCashClosingReportApi(dayStart(dateFrom), dayEnd(dateTo)),
    [dateFrom, dateTo],
    { autoRefresh: true, errorMessage: 'No se pudo cargar el cierre de caja.' },
  );

  const bySeller = useMemo(() => sortedEntries(closing?.cashBySeller), [closing]);
  const byMethod = useMemo(() => sortedEntries(closing?.totalByPaymentMethod, paymentLabel), [closing]);
  const totalAllMethods = byMethod.reduce((sum, e) => sum + e.value, 0);

  const expectedCash = Number(closing?.expectedCash || 0);
  const diff = countedCash === '' ? null : Number(countedCash) - expectedCash;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Caja"
        subtitle="Efectivo esperado, arqueo y recaudo por cajero y método de pago"
        actions={<Button size="sm" icon={RefreshCw} loading={loading} onClick={() => reload({ silent: false })}>Actualizar</Button>}
      />

      <div className="bento-card p-4">
        <DateRangeFilter from={dateFrom} to={dateTo} onChange={(from, to) => { setDateFrom(from); setDateTo(to); }} />
      </div>

      {error && <div className="p-3.5 rounded-2xl border border-red-200 bg-red-50 dark:bg-red-500/10 text-xs font-bold text-red-600">{error}</div>}

      <div className="bento-card p-6">
        <div className="flex items-center gap-3 mb-5">
          <div className="w-10 h-10 rounded-full bg-brand/10 flex items-center justify-center text-brand shrink-0">
            <Wallet className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-sm font-extrabold text-ink">Arqueo de caja</h3>
            <p className="text-[11px] text-ink-soft font-medium">Cuenta el cajón y compáralo con lo que registraron las ventas en efectivo</p>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <span className="text-[11px] font-extrabold uppercase text-ink-muted">Efectivo esperado</span>
            <div className="text-3xl font-black text-ink tabular-nums mt-0.5">{loading ? '…' : money(expectedCash)}</div>
          </div>
          <div className="space-y-2">
            <MoneyInput
              label="Efectivo contado"
              value={countedCash}
              onChange={setCountedCash}
              placeholder="Total contado en el cajón"
            />
            {diff !== null && (
              <div className={`p-3 rounded-2xl border text-xs font-bold flex items-center gap-2 ${
                diff === 0
                  ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-600 dark:text-emerald-400'
                  : diff < 0
                  ? 'bg-red-50 dark:bg-red-500/10 border-red-200 dark:border-red-500/30 text-red-600 dark:text-red-400'
                  : 'bg-amber-50 dark:bg-amber-500/10 border-amber-200 dark:border-amber-500/30 text-amber-700 dark:text-amber-400'
              }`}>
                {diff === 0 ? <CheckCircle2 className="w-4 h-4 shrink-0" /> : diff < 0 ? <MinusCircle className="w-4 h-4 shrink-0" /> : <PlusCircle className="w-4 h-4 shrink-0" />}
                <span>
                  {diff === 0 ? 'La caja cuadra exactamente.' : diff < 0 ? `Faltan ${money(Math.abs(diff))} en caja.` : `Sobran ${money(diff)} en caja.`}
                </span>
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        <SectionCard icon={Users} title="Efectivo por cajero" subtitle="Ventas en efectivo del período">
          {loading ? <LoadingRows /> : <RankedList entries={bySeller} emptyLabel="Sin ventas en efectivo en este período" />}
        </SectionCard>
        <SectionCard
          icon={CreditCard}
          title="Recaudo por método de pago"
          subtitle={`Total del período: ${money(totalAllMethods)}`}
        >
          {loading ? <LoadingRows /> : <RankedList entries={byMethod} />}
        </SectionCard>
      </div>
    </div>
  );
}
