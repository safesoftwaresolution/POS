import React, { useState } from 'react';
import { AlertCircle, ArrowRightLeft, CheckCircle2, Plus, Store } from 'lucide-react';
import useBusiness from '../hooks/useBusiness';
import useAuth from '../hooks/useAuth';
import useLoad from '../hooks/useLoad';
import useModal from '../hooks/useModal';
import { Badge, Button, DateRangeFilter, EmptyState, PageHeader } from '../components/ui';
import { AreaChart } from '../components/dashboard/charts';
import { Ranking } from '../components/dashboard/rankings';
import NewBranchModal from '../components/business/NewBranchModal';
import { getBranchesStatsApi, getBusinessOverviewApi, switchStoreApi } from '../services/businessApi';
import { formatNumber, isoDaysAgo, money, paymentLabel } from '../utils/format';
import {
  BILLING_MODE_HINTS, BILLING_MODE_LABELS, branchStatusLabel, branchStatusTone, canAddBranch, fillDailySeries,
} from '../utils/business';

function Kpi({ title, value, hint }) {
  return (
    <div className="p-4 sm:p-5 min-w-0">
      <span className="text-[10px] font-extrabold uppercase tracking-wider text-ink-muted">{title}</span>
      <div className="text-xl sm:text-2xl font-black text-ink tabular-nums mt-0.5 truncate">{value}</div>
      {hint && <span className="text-[11px] font-semibold text-ink-soft">{hint}</span>}
    </div>
  );
}

/**
 * Panel del dueño de un negocio con varias sucursales: totales de todas juntas y comparativo por sucursal.
 * Los datos salen de las sucursales del negocio del usuario; cambiar de sucursal no los modifica.
 */
export default function BusinessBento() {
  const { business, activeStoreId, loading: businessLoading, reload: reloadBusiness } = useBusiness();
  const { setSessionFromResponse } = useAuth();
  const { notify } = useModal();
  const [range, setRange] = useState(() => ({ from: isoDaysAgo(29), to: isoDaysAgo(0) }));
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState(null);
  const [switchingTo, setSwitchingTo] = useState(null);

  const { data, loading, error } = useLoad(
    () => Promise.all([getBusinessOverviewApi(range.from, range.to), getBranchesStatsApi(range.from, range.to)]),
    // Cambia el número de sucursales (alta o baja): el comparativo debe incluir la nueva.
    [range.from, range.to, business?.id, business?.branches.length],
    { enabled: Boolean(business), errorMessage: 'No se pudieron cargar las estadísticas del negocio.' },
  );
  const [overview, branchesStats] = data || [];

  const switchTo = async (branch) => {
    setSwitchingTo(branch.storeId);
    try {
      setSessionFromResponse(await switchStoreApi(branch.storeId));
      window.location.assign('/');
    } catch (e) {
      setSwitchingTo(null);
      notify(e?.message || 'No se pudo cambiar de sucursal.');
    }
  };

  if (businessLoading) return null;
  if (!business) {
    return (
      <EmptyState
        icon={Store}
        title="Tu local no tiene sucursales"
        description="Esta pantalla aparece cuando la plataforma agrupa varias sucursales bajo tu cuenta."
      />
    );
  }

  const totals = overview?.totals;
  const series = overview ? fillDailySeries(overview.daily, range.from, range.to) : [];
  const methods = (overview?.byPaymentMethod || []).map((m) => ({ label: paymentLabel(m.paymentMethod), value: Number(m.amount) }));
  const branchRanking = (branchesStats?.branches || []).map((b) => ({ label: b.name, value: Number(b.totals.netAmount) }));
  const canAdd = canAddBranch(business);

  return (
    <div className="space-y-6 pb-12">
      <PageHeader
        title={business.name}
        subtitle={`${business.branches.length} de ${business.maxBranches} sucursales · ${BILLING_MODE_LABELS[business.billingMode]}`}
        actions={(
          <Button
            icon={Plus}
            onClick={() => { setCreated(null); setCreating(true); }}
            disabled={!canAdd}
            title={canAdd ? undefined : 'Tu negocio alcanzó su máximo de sucursales'}
          >
            Nueva sucursal
          </Button>
        )}
      />

      {created && (
        <div className="p-3 bg-emerald-50 dark:bg-emerald-950/30 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 shrink-0" />
          <span>«{created.name}» quedó pendiente de revisión. Podrás operarla en cuanto la plataforma la apruebe.</span>
        </div>
      )}

      <div className="bento-card hover:transform-none p-4 sm:p-5">
        <span className="block text-[11px] font-extrabold uppercase tracking-wider text-ink-muted mb-2">Periodo</span>
        <DateRangeFilter from={range.from} to={range.to} onChange={(from, to) => setRange({ from, to })} />
      </div>

      {error && (
        <div className="p-3 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800/40 rounded-xl text-xs font-bold flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {loading && !overview && <div className="py-16 text-center text-xs font-bold text-ink-muted">Cargando...</div>}

      {overview && totals && (
        <>
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            <div className="lg:col-span-8 bento-card hover:transform-none p-6 sm:p-7">
              <span className="text-[11px] font-extrabold uppercase tracking-wider text-ink-muted">Venta neta · todas las sucursales</span>
              <div className="text-4xl sm:text-5xl font-black tracking-tight text-ink tabular-nums mt-1">{money(totals.netAmount)}</div>
              <div className="mt-2">
                {series.every((p) => p.value === 0)
                  ? <p className="text-xs text-ink-muted font-semibold py-16 text-center">Sin ventas en este periodo</p>
                  : <AreaChart data={series} formatValue={money} />}
              </div>
              <div className="mt-4 pt-3 border-t border-line grid grid-cols-2 sm:grid-cols-4 divide-x divide-line">
                <Kpi title="Ventas" value={formatNumber(totals.salesCount)} hint={money(totals.salesAmount)} />
                <Kpi title="Ticket promedio" value={money(totals.averageTicket)} />
                <Kpi title="Devoluciones" value={formatNumber(totals.returnsCount)} hint={money(totals.returnsAmount)} />
                <Kpi title="Sucursales" value={overview.branchCount} hint="en el negocio" />
              </div>
            </div>

            <div className="lg:col-span-4 bento-card hover:transform-none p-6">
              <h3 className="text-sm font-extrabold text-ink">Métodos de pago</h3>
              <p className="text-[11px] text-ink-soft font-medium mb-4">Importe vendido por forma de pago</p>
              <Ranking entries={methods} formatValue={money} emptyLabel="Sin ventas en este periodo" />
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            <div className="lg:col-span-7 bento-card hover:transform-none overflow-hidden">
              <div className="p-6 pb-3">
                <h3 className="text-sm font-extrabold text-ink">Comparativo por sucursal</h3>
                <p className="text-[11px] text-ink-soft font-medium">Ordenadas por venta neta del periodo</p>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-xs">
                  <thead>
                    <tr className="text-left text-[10px] font-extrabold uppercase tracking-wider text-ink-muted border-y border-line">
                      <th className="px-6 py-2.5">Sucursal</th>
                      <th className="px-3 py-2.5 text-right">Ventas</th>
                      <th className="px-3 py-2.5 text-right">Neto</th>
                      <th className="px-3 py-2.5 text-right">Ticket</th>
                      <th className="px-3 py-2.5 text-right">Stock bajo</th>
                      <th className="px-6 py-2.5" />
                    </tr>
                  </thead>
                  <tbody>
                    {branchesStats.branches.map((b) => (
                      <tr key={b.storeId} className="border-b border-line last:border-b-0">
                        <td className="px-6 py-3">
                          <div className="font-bold text-ink">{b.name}</div>
                          <div className="flex items-center gap-1.5 mt-0.5">
                            <span className="text-[10px] font-semibold text-ink-muted">{b.main ? 'Principal' : 'Sucursal'}</span>
                            {b.status !== 'ACTIVE' && <Badge tone={branchStatusTone(b.status)}>{branchStatusLabel(b.status)}</Badge>}
                          </div>
                        </td>
                        <td className="px-3 py-3 text-right font-bold tabular-nums">{formatNumber(b.totals.salesCount)}</td>
                        <td className="px-3 py-3 text-right font-black tabular-nums">{money(b.totals.netAmount)}</td>
                        <td className="px-3 py-3 text-right font-semibold tabular-nums text-ink-soft">{money(b.totals.averageTicket)}</td>
                        <td className="px-3 py-3 text-right font-bold tabular-nums">
                          {b.lowStockProducts > 0 ? <span className="text-amber-600 dark:text-amber-400">{b.lowStockProducts}</span> : '—'}
                        </td>
                        <td className="px-6 py-3 text-right">
                          {b.storeId === activeStoreId ? (
                            <Badge tone="brand">Aquí estás</Badge>
                          ) : (
                            <Button
                              variant="outline" size="sm" icon={ArrowRightLeft}
                              loading={switchingTo === b.storeId}
                              disabled={switchingTo !== null}
                              onClick={() => switchTo(b)}
                            >
                              Ir
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="lg:col-span-5 bento-card hover:transform-none p-6">
              <h3 className="text-sm font-extrabold text-ink">Ranking de ventas netas</h3>
              <p className="text-[11px] text-ink-soft font-medium mb-4">Participación de cada sucursal en el total</p>
              <Ranking entries={branchRanking} formatValue={money} emptyLabel="Aún no hay sucursales" />
              <p className="mt-5 pt-4 border-t border-line text-[11px] text-ink-muted font-medium">
                {BILLING_MODE_HINTS[business.billingMode]}
              </p>
            </div>
          </div>
        </>
      )}

      {creating && (
        <NewBranchModal
          business={business}
          onClose={() => setCreating(false)}
          onCreated={(branch) => {
            setCreating(false);
            setCreated(branch);
            reloadBusiness({ silent: false });
          }}
        />
      )}
    </div>
  );
}
